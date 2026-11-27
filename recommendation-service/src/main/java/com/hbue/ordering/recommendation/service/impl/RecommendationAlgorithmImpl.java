package com.hbue.ordering.recommendation.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.recommendation.algorithm.SpecificationPreferenceNormalizer;
import com.hbue.ordering.recommendation.model.*;
import com.hbue.ordering.recommendation.service.RecommendationAlgorithm;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 首期可解释混合推荐实现；所有打分在推荐服务本地完成，不在首页请求里逐菜调用外部服务。
 */
@Service
public class RecommendationAlgorithmImpl implements RecommendationAlgorithm {
    private static final int MAX_PROFILE_ORDERS = 500;
    private static final int MAX_CANDIDATES = 2000;
    private static final int MAX_HOT_PRODUCTS = 50;
    private static final int MAX_SEARCHES = 5;
    private static final double PURCHASE_HALF_LIFE_DAYS = 30.0;
    private static final double SEARCH_HALF_LIFE_DAYS = 3.0;
    private static final double PURCHASE_CAP_PER_ORDER_PRODUCT = 3.0;
    private static final double PURCHASE_SCORE_DENOMINATOR = Math.log(6.0);
    private static final Pattern TEXT_TOKEN = Pattern.compile("[\\p{IsHan}]+|[a-z0-9]+");

    private final ObjectMapper json;
    private final SpecificationPreferenceNormalizer specificationNormalizer = new SpecificationPreferenceNormalizer();

    /**
     * JSON 解析器用于本服务持久化的历史选项与查询结果快照；解析失败不能猜测用户偏好。
     */
    public RecommendationAlgorithmImpl(ObjectMapper json) {
        this.json = json;
    }

    /**
     * 按用户行为、当前有效候选和既有统计热度计算菜品分数；先逐明细核验退款，
     * 再按订单和菜品合并规格行，最后统一应用兴趣上限。
     */
    @Override
    public RecommendationAlgorithm.RankedResult rank(Long userId, Long restaurantId, int limit,
                                                     List<RecommendationProductFeatureDO> candidates,
                                                     List<RecommendationProductFeatureDO> historicalProducts,
                                                     List<RecommendationOrderSnapshotDO> orders,
                                                     List<RecommendationOrderItemDO> lines,
                                                     List<RecommendationFavoriteStateDO> favorites,
                                                     List<RecommendationSearchIntentDO> searches,
                                                     List<RecommendationHotProductDO> hot,
                                                     LocalDateTime now) {
        if (candidates == null || orders == null || lines == null || favorites == null || searches == null || hot == null) {
            throw new IllegalArgumentException("推荐输入快照不能为空");
        }
        if (candidates.size() > MAX_CANDIDATES || orders.size() > MAX_PROFILE_ORDERS
                || hot.size() > MAX_HOT_PRODUCTS || searches.size() > 20) {
            throw new IllegalArgumentException("推荐输入超过有界处理上限");
        }
        if (candidates.isEmpty()) return new RecommendationAlgorithm.RankedResult("COLD_START", List.of());

        Map<Long, RecommendationOrderSnapshotDO> orderById = new HashMap<>();
        for (RecommendationOrderSnapshotDO order : orders) orderById.put(order.getOrderId(), order);
        Map<Long, Map<Long, List<RecommendationOrderItemDO>>> linesByOrderAndProduct = new HashMap<>();
        for (RecommendationOrderItemDO line : lines) {
            RecommendationOrderSnapshotDO order = line == null ? null : orderById.get(line.getOrderId());
            if (order == null || line.getProductId() == null || line.getProductId() <= 0
                    || line.getPurchasedQuantity() == null || line.getPurchasedQuantity() <= 0
                    || line.getRefundedQuantity() == null || line.getRefundedQuantity() < 0
                    || line.getRefundedQuantity() > line.getPurchasedQuantity()) {
                throw new IllegalStateException("推荐订单明细快照违反购买或退款数量约束");
            }
            linesByOrderAndProduct.computeIfAbsent(order.getOrderId(), ignored -> new HashMap<>())
                    .computeIfAbsent(line.getProductId(), ignored -> new ArrayList<>()).add(line);
        }

        Set<Long> productFavorites = new HashSet<>();
        Set<Long> windowFavorites = new HashSet<>();
        for (RecommendationFavoriteStateDO favorite : favorites) {
            if (favorite == null || !Boolean.TRUE.equals(favorite.getActive())) continue;
            if ("PRODUCT".equals(favorite.getTargetType())) productFavorites.add(favorite.getTargetId());
            if ("WINDOW".equals(favorite.getTargetType())) windowFavorites.add(favorite.getTargetId());
        }

        Map<Long, Double> purchaseRaw = new HashMap<>();
        Map<String, Double> tasteWeights = new HashMap<>();
        Set<Long> purchasedRecently = new HashSet<>();
        LocalDateTime recentPurchaseCutoff = now.minusHours(2);
        for (var orderEntry : linesByOrderAndProduct.entrySet()) {
            RecommendationOrderSnapshotDO order = orderById.get(orderEntry.getKey());
            double orderDecay = decay(daysBetween(order.getPaidAt(), now), PURCHASE_HALF_LIFE_DAYS);
            for (var productEntry : orderEntry.getValue().entrySet()) {
                int totalNetQuantity = 0;
                for (RecommendationOrderItemDO line : productEntry.getValue()) {
                    totalNetQuantity = Math.addExact(totalNetQuantity,
                            line.getPurchasedQuantity() - line.getRefundedQuantity());
                }
                if (totalNetQuantity <= 0) continue;
                double cappedQuantity = Math.min(totalNetQuantity, PURCHASE_CAP_PER_ORDER_PRODUCT);
                purchaseRaw.merge(productEntry.getKey(), cappedQuantity * orderDecay, Double::sum);
                if (!order.getPaidAt().isBefore(recentPurchaseCutoff)) purchasedRecently.add(productEntry.getKey());

                // 多规格行先合并总净量，再按每行净份数分摊封顶额度，退款只撤销其对应口味选择。
                for (RecommendationOrderItemDO line : productEntry.getValue()) {
                    int lineNet = line.getPurchasedQuantity() - line.getRefundedQuantity();
                    if (lineNet <= 0) continue;
                    double lineWeight = cappedQuantity * lineNet / totalNetQuantity * orderDecay;
                    for (String tag : selectedTasteTags(line.getSpecificationSelectionSnapshot())) {
                        tasteWeights.merge(tag, lineWeight, Double::sum);
                    }
                }
            }
        }

        Map<Long, Double> itemInterest = new HashMap<>();
        for (Long productId : union(purchaseRaw.keySet(), productFavorites)) {
            double purchase = Math.min(1.0, Math.log1p(purchaseRaw.getOrDefault(productId, 0.0))
                    / PURCHASE_SCORE_DENOMINATOR);
            double favorite = productFavorites.contains(productId) ? 1.0 : 0.0;
            itemInterest.put(productId, (6.0 * purchase + 4.0 * favorite) / 10.0);
        }

        Map<Long, RecommendationProductFeatureDO> featureById = new LinkedHashMap<>();
        for (RecommendationProductFeatureDO feature : historicalProducts)
            featureById.put(feature.getProductId(), feature);
        for (RecommendationProductFeatureDO candidate : candidates)
            featureById.put(candidate.getProductId(), candidate);
        Map<Long, Map<String, Double>> vectors = contentVectors(featureById.values());
        Map<String, Double> profile = userProfile(vectors, itemInterest);
        Map<Long, Double> queryScores = searchScores(searches, now, restaurantId);
        // 热度分直接使用统计服务的毛销量口径（已支付份数，退款不冲减）；上方用户兴趣分则用
        // "购买份数 - 退款份数"的净量。两者口径不同是有意为之：热榜衡量菜品整体受欢迎程度，
        // 兴趣刻画用户实际消费，不能互相替代，也不应在本服务内改算统计口径。
        Map<Long, Long> salesByProduct = new HashMap<>();
        long maximumSales = 0;
        for (RecommendationHotProductDO item : hot) {
            if (item != null && item.getProductId() != null && item.getSales() != null && item.getSales() > 0) {
                salesByProduct.put(item.getProductId(), item.getSales());
                maximumSales = Math.max(maximumSales, item.getSales());
            }
        }

        boolean useContent = !profile.isEmpty() || !tasteWeights.isEmpty();
        boolean useItemInterest = itemInterest.values().stream().anyMatch(value -> value > 0.0);
        boolean useSearch = queryScores.values().stream().anyMatch(value -> value > 0.0);
        boolean useWindowFavorite = candidates.stream().anyMatch(candidate -> windowFavorites.contains(candidate.getWindowId()));
        boolean useHot = candidates.stream().anyMatch(candidate -> salesByProduct.getOrDefault(candidate.getProductId(), 0L) > 0);
        double totalWeight = (useContent ? 0.35 : 0) + (useItemInterest ? 0.20 : 0)
                + (useSearch ? 0.25 : 0) + (useWindowFavorite ? 0.05 : 0) + (useHot ? 0.15 : 0);
        boolean personalized = useContent || useItemInterest || useSearch || useWindowFavorite;

        List<RecommendationAlgorithm.RankedItem> ranked = new ArrayList<>(candidates.size());
        long twoHourBucket = now.toEpochSecond(ZoneOffset.UTC) / 7200;
        for (RecommendationProductFeatureDO candidate : candidates) {
            long productId = candidate.getProductId();
            double content = cosine(profile, vectors.getOrDefault(productId, Map.of()));
            double flavor = tasteMatch(tasteWeights, availableTasteTags(candidate.getSpecificationGroups()));
            if (!tasteWeights.isEmpty()) content = 0.9 * content + 0.1 * flavor;
            double item = itemInterest.getOrDefault(productId, 0.0);
            double query = queryScores.getOrDefault(productId, 0.0);
            double window = windowFavorites.contains(candidate.getWindowId()) ? 1.0 : 0.0;
            double sales = salesByProduct.getOrDefault(productId, 0L);
            double hotScore = useHot && sales > 0 ? Math.log1p(sales) / Math.log1p(maximumSales) : 0.0;
            double numerator = (useContent ? 0.35 * content : 0) + (useItemInterest ? 0.20 * item : 0)
                    + (useSearch ? 0.25 * query : 0) + (useWindowFavorite ? 0.05 * window : 0)
                    + (useHot ? 0.15 * hotScore : 0);
            double score = totalWeight == 0 ? 0 : numerator / totalWeight;
            if (purchasedRecently.contains(productId)) score *= 0.5;
            ranked.add(new RecommendationAlgorithm.RankedItem(candidate, reason(productId, item, productFavorites, flavor,
                    query, window, content, hotScore), score, tieKey(userId, productId, twoHourBucket)));
        }
        ranked.sort(Comparator.comparingDouble(RecommendationAlgorithm.RankedItem::score).reversed()
                .thenComparingLong(RecommendationAlgorithm.RankedItem::tieKey)
                .thenComparing(item -> item.product().getProductId()));
        return new RecommendationAlgorithm.RankedResult(personalized ? "PERSONALIZED" : "COLD_START", diversify(ranked, limit));
    }

    /**
     * 向量以名称、分类文本、描述、窗口及窗口内分类构成，不把“可选规格”误作实际口味。
     */
    private Map<Long, Map<String, Double>> contentVectors(Iterable<RecommendationProductFeatureDO> features) {
        Map<Long, Map<String, Double>> raw = new HashMap<>();
        Map<String, Integer> documentFrequency = new HashMap<>();
        for (RecommendationProductFeatureDO feature : features) {
            Map<String, Double> vector = new HashMap<>();
            addText(vector, feature.getProductName(), 3.0);
            addText(vector, feature.getCategoryName(), 1.5);
            addText(vector, feature.getDescription(), 0.5);
            vector.merge("WINDOW:" + feature.getWindowId(), 1.5, Double::sum);
            if (feature.getCategoryId() != null) {
                vector.merge("CATEGORY:" + feature.getWindowId() + ":" + feature.getCategoryId(), 1.0, Double::sum);
            }
            raw.put(feature.getProductId(), vector);
            for (String term : vector.keySet()) documentFrequency.merge(term, 1, Integer::sum);
        }
        Map<Long, Map<String, Double>> normalized = new HashMap<>();
        int count = Math.max(raw.size(), 1);
        for (var entry : raw.entrySet()) {
            Map<String, Double> vector = new HashMap<>();
            double normSquared = 0;
            for (var term : entry.getValue().entrySet()) {
                double inverseDocumentFrequency = Math.log((count + 1.0)
                        / (documentFrequency.getOrDefault(term.getKey(), 0) + 1.0)) + 1.0;
                double value = term.getValue() * inverseDocumentFrequency;
                vector.put(term.getKey(), value);
                normSquared += value * value;
            }
            if (normSquared > 0) {
                double norm = Math.sqrt(normSquared);
                vector.replaceAll((term, value) -> value / norm);
            }
            normalized.put(entry.getKey(), vector);
        }
        return normalized;
    }

    /**
     * 名称权重大于描述；中文切为字符二元组，拉丁文本按小写词项累计词频。
     */
    private void addText(Map<String, Double> vector, String text, double weight) {
        if (text == null || text.isBlank()) return;
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        Matcher matcher = TEXT_TOKEN.matcher(normalized);
        while (matcher.find()) {
            String token = matcher.group();
            if (token.codePoints().allMatch(Character::isIdeographic)) {
                int[] points = token.codePoints().toArray();
                if (points.length == 1) vector.merge("TEXT:" + token, weight, Double::sum);
                else for (int index = 0; index < points.length - 1; index++) {
                    vector.merge("TEXT:" + new String(points, index, 2), weight, Double::sum);
                }
            } else {
                vector.merge("TEXT:" + token, weight, Double::sum);
            }
        }
    }

    /**
     * 把本人有效商品兴趣向量按 E 分数求和并单位化；零画像安全返回空向量。
     */
    private Map<String, Double> userProfile(Map<Long, Map<String, Double>> vectors,
                                            Map<Long, Double> interests) {
        Map<String, Double> profile = new HashMap<>();
        for (var interest : interests.entrySet()) {
            Map<String, Double> vector = vectors.get(interest.getKey());
            if (vector == null || interest.getValue() <= 0) continue;
            vector.forEach((term, value) -> profile.merge(term, value * interest.getValue(), Double::sum));
        }
        normalize(profile);
        return profile;
    }

    /**
     * 对近期成功搜索的首屏排名求折扣倒数分，分页和重试已由 queryId 幂等入口合并。
     */
    private Map<Long, Double> searchScores(List<RecommendationSearchIntentDO> searches,
                                           LocalDateTime now, Long restaurantId) {
        Map<Long, Double> result = new HashMap<>();
        double denominator = 0;
        int usedQueries = 0;
        for (RecommendationSearchIntentDO query : searches) {
            if (usedQueries >= MAX_SEARCHES) break;
            if (restaurantId != null && query.getRestaurantId() != null
                    && !restaurantId.equals(query.getRestaurantId())) continue;
            double queryWeight = decay(daysBetween(query.getCreatedAt(), now), SEARCH_HALF_LIFE_DAYS);
            denominator += queryWeight;
            usedQueries++;
            try {
                JsonNode ids = json.readTree(query.getRankedProductIds());
                if (ids == null || !ids.isArray()) throw new IllegalStateException("搜索候选快照格式错误");
                int rank = 0;
                for (JsonNode id : ids) {
                    if (!id.canConvertToLong() || id.asLong() <= 0) throw new IllegalStateException("搜索候选编号错误");
                    rank++;
                    result.merge(id.asLong(), queryWeight / (Math.log(rank + 1.0) / Math.log(2.0)), Double::sum);
                }
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalStateException("搜索候选快照无法解析", exception);
            }
        }
        double safeDenominator = Math.max(1.0, denominator);
        result.replaceAll((productId, score) -> Math.min(1.0, score / safeDenominator));
        return result;
    }

    /**
     * 根据用户实际购买时保存的结构化选项快照生成兴趣标签，绝不从可选项或自由文本猜测。
     */
    private Set<String> selectedTasteTags(String snapshot) {
        Set<String> tags = new HashSet<>();
        if (snapshot == null || snapshot.isBlank()) return tags;
        try {
            JsonNode groups = json.readTree(snapshot);
            if (groups == null || !groups.isArray()) throw new IllegalStateException("订单规格选择快照格式错误");
            for (JsonNode selection : groups) {
                String tag = specificationNormalizer.normalize(selection.path("groupName").asText(),
                        selection.path("optionName").asText());
                if (tag != null) tags.add(tag);
            }
            return tags;
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("订单规格选择快照无法解析", exception);
        }
    }

    /**
     * 当前商品规格只提供可能匹配的标签，不会单独增加用户偏好权重。
     */
    private Set<String> availableTasteTags(String specificationGroups) {
        Set<String> tags = new HashSet<>();
        if (specificationGroups == null || specificationGroups.isBlank()) return tags;
        try {
            JsonNode groups = json.readTree(specificationGroups);
            if (groups == null || !groups.isArray()) return tags;
            for (JsonNode group : groups) {
                String groupName = group.path("groupName").asText();
                JsonNode options = group.path("options");
                if (!options.isArray()) continue;
                for (JsonNode option : options) {
                    String tag = specificationNormalizer.normalize(groupName, option.path("optionName").asText());
                    if (tag != null) tags.add(tag);
                }
            }
            return tags;
        } catch (Exception exception) {
            throw new IllegalStateException("当前商品规格快照无法解析", exception);
        }
    }

    /**
     * 命中的明确口味维度按其购买强度占比得分，未命中未知选项不会被当成讨厌。
     */
    private double tasteMatch(Map<String, Double> preferences, Set<String> available) {
        if (preferences.isEmpty()) return 0;
        double total = 0;
        double matched = 0;
        for (var entry : preferences.entrySet()) {
            total += entry.getValue();
            if (available.contains(entry.getKey())) matched += entry.getValue();
        }
        return total <= 0 ? 0 : matched / total;
    }

    /**
     * 计算单位化向量余弦；空画像或空商品向量返回0，不产生NaN。
     */
    private double cosine(Map<String, Double> left, Map<String, Double> right) {
        if (left.isEmpty() || right.isEmpty()) return 0;
        double dot = 0;
        for (var term : left.entrySet()) dot += term.getValue() * right.getOrDefault(term.getKey(), 0.0);
        return Math.max(0, Math.min(1, dot));
    }

    /**
     * 对稀疏向量做 L2 归一化；零向量保持空值。
     */
    private void normalize(Map<String, Double> vector) {
        double normSquared = vector.values().stream().mapToDouble(value -> value * value).sum();
        if (normSquared <= 0) {
            vector.clear();
            return;
        }
        double norm = Math.sqrt(normSquared);
        vector.replaceAll((term, value) -> value / norm);
    }

    /**
     * 决定可向顾客展示的原因代码，不返回评分或原始搜索词。
     */
    private String reason(Long productId, double itemInterest, Set<Long> productFavorites, double flavor,
                          double query, double window, double content, double hot) {
        if (itemInterest > 0 && !productFavorites.contains(productId)) return "PURCHASED";
        if (productFavorites.contains(productId)) return "FAVORITED";
        if (flavor > 0) return "FLAVOR_MATCH";
        if (query > 0) return "SEARCH_RELATED";
        if (window > 0) return "WINDOW_FAVORITE";
        if (content > 0) return "CONTENT_SIMILAR";
        if (hot > 0) return "POPULAR";
        return "EXPLORE";
    }

    /**
     * 为探索候选生成按两小时轮换、对同用户同时间窗稳定的打散键。
     */
    private long tieKey(Long userId, Long productId, long bucket) {
        int hash = (userId + ":" + productId + ":" + bucket).hashCode();
        return Integer.toUnsignedLong(hash);
    }

    /**
     * 多样性先限制每窗口2道；候选不足时逐轮放宽，仍不足就少返回而不复制商品。
     */
    private List<RecommendationAlgorithm.RankedItem> diversify(List<RecommendationAlgorithm.RankedItem> ranked, int limit) {
        for (int perWindow = 2; perWindow <= limit; perWindow++) {
            List<RecommendationAlgorithm.RankedItem> selected = new ArrayList<>(limit);
            Set<Long> selectedIds = new HashSet<>();
            Map<Long, Integer> counts = new HashMap<>();
            for (RecommendationAlgorithm.RankedItem item : ranked) {
                Long windowId = item.product().getWindowId();
                if (counts.getOrDefault(windowId, 0) >= perWindow || !selectedIds.add(item.product().getProductId()))
                    continue;
                selected.add(item);
                counts.merge(windowId, 1, Integer::sum);
                if (selected.size() == limit) return selected;
            }
            if (selected.size() == ranked.size()) return selected;
        }
        return ranked.stream().limit(limit).toList();
    }

    /**
     * 半衰期衰减；未来时间按年龄0处理，避免时钟偏差产生大于1的权重。
     */
    private double decay(double ageDays, double halfLifeDays) {
        return Math.pow(2.0, -Math.max(0.0, ageDays) / halfLifeDays);
    }

    /**
     * 计算两时刻的非负天数，旧时间戳缺失时按已衰减掉处理。
     */
    private double daysBetween(LocalDateTime occurredAt, LocalDateTime now) {
        if (occurredAt == null) return Double.POSITIVE_INFINITY;
        return Math.max(0, Duration.between(occurredAt, now).toSeconds() / 86400.0);
    }

    /**
     * 合并两个 ID 集合并去空，避免为同一商品重复构造特征。
     */
    private Set<Long> union(Set<Long> first, Set<Long> second) {
        Set<Long> values = new HashSet<>(first);
        values.addAll(second);
        return values;
    }

}
