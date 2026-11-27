package com.hbue.ordering.search.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.redis.RedisOperationsService;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.product.api.dto.ProductSearchDocument;
import com.hbue.ordering.product.api.searchclient.ProductSearchSourceClient;
import com.hbue.ordering.search.document.MenuProductDocument;
import com.hbue.ordering.search.service.ProductSearchService;
import com.hbue.ordering.search.support.SearchIndexLock;
import com.hbue.ordering.search.support.SearchIndexManager;
import com.hbue.ordering.search.vo.ProductSearchPageVO;
import com.hbue.ordering.search.vo.ProductSearchResultVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.DeleteQuery;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.data.elasticsearch.core.query.StringQuery;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

/**
 * 商品搜索查询、窗口索引刷新与全量重建。
 */
@Service
public class ProductSearchServiceImpl implements ProductSearchService {
    /** 联想候选数量上限：与接口参数约束保持一致，防止一次拉取过多文档。 */
    private static final int SUGGEST_MAX_LIMIT = 20;
    /** 去重前的候选抓取倍数：同一菜名可能出现在多个档口，多取一些保证去重后仍有足够条数。 */
    private static final int SUGGEST_PROBE_FACTOR = 3;
    /** 联想结果缓存时长（秒）：相同前缀 60 秒内直接复用，新上架菜品的可见延迟不超过该值。 */
    private static final long SUGGEST_CACHE_TTL_SECONDS = 60L;
    /** 联想缓存键前缀，缓存键形如 `search:suggest:{limit}:{归一化关键词}`。 */
    private static final String SUGGEST_CACHE_PREFIX = "search:suggest:";
    /** 联想限频键前缀，按用户维度独立计数。 */
    private static final String SUGGEST_RATE_PREFIX = "search:suggest:rate:";
    /** 单个用户在限频窗口内的联想请求上限。 */
    private static final long SUGGEST_RATE_LIMIT = 60L;
    /** 限频窗口（秒）：与缓存 TTL 一致，避免两套机制的时间尺度不同造成理解成本。 */
    private static final long SUGGEST_RATE_WINDOW_SECONDS = 60L;

    private final ElasticsearchOperations elasticsearch;
    private final ProductSearchSourceClient productSource;
    private final SearchIndexLock indexLock;
    private final SearchIndexManager indexManager;
    private final ObjectMapper json;
    private final String internalToken;
    /**
     * 联想接口的缓存与限频依赖。
     *
     * <p>Redis 只承载可丢弃的加速数据：读缓存失败回退到 ES，限频计数失败按未超限处理，
     * 都不能让联想接口不可用，也不能影响既有搜索链路。</p>
     */
    private final RedisOperationsService redis;

    public ProductSearchServiceImpl(ElasticsearchOperations elasticsearch, ProductSearchSourceClient productSource,
                                    SearchIndexLock indexLock, SearchIndexManager indexManager, ObjectMapper json,
                                    RedisOperationsService redis,
                                    @Value("${internal.scope.token:}") String internalToken) {
        this.elasticsearch = elasticsearch;
        this.productSource = productSource;
        this.indexLock = indexLock;
        this.indexManager = indexManager;
        this.json = json;
        this.redis = redis;
        this.internalToken = internalToken;
    }

    public ProductSearchPageVO search(String keyword, Long restaurantId, Long windowId, Long categoryId, int page, int size) {
        String q;
        try {
            q = json.writeValueAsString(keyword.trim());
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("搜索词无法编码", ex);
        }
        StringBuilder query = new StringBuilder("{\"bool\":{\"must\":[{\"multi_match\":{\"query\":")
                .append(q).append(",\"type\":\"bool_prefix\",\"fields\":[\"productName\",\"productName._2gram\",\"productName._3gram\",\"description\",\"categoryName\",\"windowName\",\"restaurantName\"]}}],\"filter\":[");
        List<String> filters = new ArrayList<>();
        if (restaurantId != null) filters.add("{\"term\":{\"restaurantId\":" + restaurantId + "}}");
        if (windowId != null) filters.add("{\"term\":{\"windowId\":" + windowId + "}}");
        if (categoryId != null) filters.add("{\"term\":{\"categoryId\":" + categoryId + "}}");
        query.append(String.join(",", filters)).append("]}} ");
        StringQuery request = new StringQuery(query.toString());
        request.setPageable(PageRequest.of(page - 1, size));
        SearchHits<MenuProductDocument> hits = elasticsearch.search(request, MenuProductDocument.class);
        List<ProductSearchResultVO> results = hits.getSearchHits().stream()
                .map(SearchHit::getContent).map(this::toResult).toList();
        return new ProductSearchPageVO(keyword.trim(), page, size, hits.getTotalHits(), results);
    }

    /**
     * 按输入前缀返回菜名联想；失败、超限或被缓存绕过时都不影响既有搜索。
     *
     * <p>流程：先按用户固定窗口限频 → 命中 Redis 前缀缓存直接返回 → 未命中则用
     * {@code bool_prefix} 查询菜名及其 n-gram 子字段 → 按菜名去重后取前 limit 条 → 回写缓存。
     * 查询只覆盖菜名字段，不查描述、档口和餐厅，避免「输入联想」混入与菜名无关的结果。</p>
     */
    @Override
    public List<String> suggest(String keyword, int limit, String userUuid) {
        // 关键词为空时前端不会调用，这里仍做兜底，避免空查询打穿 ES。
        if (keyword == null) return List.of();
        String normalized = keyword.trim();
        if (normalized.isEmpty()) return List.of();
        // 条数收敛到接口允许范围，异常值不会放大 ES 抓取量。
        int size = Math.max(1, Math.min(limit, SUGGEST_MAX_LIMIT));
        // 限频超限直接返回空列表：下拉候选可以缺失，但不该让高频输入拖慢或打爆搜索服务。
        if (isSuggestRateLimited(userUuid)) return List.of();

        String cacheKey = SUGGEST_CACHE_PREFIX + size + ":" + normalized.toLowerCase(Locale.ROOT);
        // 缓存命中直接复用；缓存不可用或内容损坏时静默回退到 ES。
        try {
            String cached = redis.getValue(cacheKey);
            if (cached != null) return json.readValue(cached, new TypeReference<List<String>>() { });
        } catch (JsonProcessingException | RuntimeException ignored) {
            // 缓存只是加速手段，读失败不影响本次联想。
        }

        List<String> suggestions = querySuggestions(normalized, size);

        // 只缓存成功查询结果（含空列表），避免空结果反复打到 ES；写失败仅放弃本次缓存。
        try {
            redis.setValue(cacheKey, json.writeValueAsString(suggestions),
                    Duration.ofSeconds(SUGGEST_CACHE_TTL_SECONDS));
        } catch (JsonProcessingException | RuntimeException ignored) {
            // 缓存写入失败不影响本次返回。
        }
        return suggestions;
    }

    /**
     * 执行 ES 前缀查询并按菜名去重。
     *
     * @param keyword 已 trim 的关键词
     * @param size    期望返回条数
     * @return 去重后的菜名列表
     */
    private List<String> querySuggestions(String keyword, int size) {
        String quoted;
        try {
            // 关键词通过 JSON 序列化转义后拼入 DSL，业务值不直接参与字符串拼接。
            quoted = json.writeValueAsString(keyword);
        } catch (JsonProcessingException ex) {
            // 与搜索保持一致：无法编码时视为参数错误，由上层统一错误处理。
            throw new IllegalArgumentException("联想词无法编码", ex);
        }
        String query = "{\"bool\":{\"must\":[{\"multi_match\":{\"query\":" + quoted
                + ",\"type\":\"bool_prefix\",\"fields\":[\"productName\",\"productName._2gram\",\"productName._3gram\"]}}]}}";
        StringQuery request = new StringQuery(query);
        // 多取若干条用于去重：同名菜可能分布在多个档口，直接取 limit 条会因去重而不足。
        request.setPageable(PageRequest.of(0, size * SUGGEST_PROBE_FACTOR));
        SearchHits<MenuProductDocument> hits = elasticsearch.search(request, MenuProductDocument.class);
        Set<String> names = new LinkedHashSet<>();
        for (SearchHit<MenuProductDocument> hit : hits.getSearchHits()) {
            String name = hit.getContent().getProductName();
            // 空菜名无法作为候选，跳过；LinkedHashSet 保留 ES 相关性排序并完成去重。
            if (name == null || name.isBlank()) continue;
            names.add(name.trim());
            if (names.size() >= size) break;
        }
        return List.copyOf(names);
    }

    /**
     * 判断当前用户是否已超出联想限频。
     *
     * <p>使用固定窗口计数：首次请求时设置 60 秒 TTL，后续自增不刷新 TTL。
     * Redis 不可用时返回 false（不限频），优先保证联想可用性；搜索主链路不受影响。</p>
     *
     * @param userUuid 当前用户公开 UUID
     * @return true 表示本次请求应降级为空候选
     */
    private boolean isSuggestRateLimited(String userUuid) {
        String subject = (userUuid == null || userUuid.isBlank()) ? "anonymous" : userUuid;
        try {
            Long count = redis.increment(SUGGEST_RATE_PREFIX + subject,
                    Duration.ofSeconds(SUGGEST_RATE_WINDOW_SECONDS));
            return count != null && count > SUGGEST_RATE_LIMIT;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * 当前窗口按主库快照整体替换，事件重复消费会得到相同结果。
     */
    public void refreshWindow(Long windowId) {
        if (windowId == null || windowId <= 0) throw new IllegalArgumentException("窗口 ID 无效");
        indexLock.withReadLock(() -> indexLock.withWindowLock(windowId, () -> {
            ApiResponse<List<ProductSearchDocument>> response = productSource.loadWindowDocuments(internalToken, windowId);
            List<ProductSearchDocument> snapshot = requireData(response, "商品服务未返回窗口数据");
            Query deleteWindow = new StringQuery("{\"term\":{\"windowId\":" + windowId + "}}");
            elasticsearch.delete(DeleteQuery.builder(deleteWindow).build(), MenuProductDocument.class);
            List<MenuProductDocument> documents = snapshot.stream().map(this::toDocument).toList();
            documents.forEach(elasticsearch::save);
            return null;
        }));
    }

    @Override
    public void refreshProduct(Long windowId, Long productId) {
        if (windowId == null || windowId <= 0 || productId == null || productId <= 0) {
            throw new IllegalArgumentException("窗口或菜品 ID 无效");
        }
        indexLock.withReadLock(() -> indexLock.withWindowLock(windowId, () -> {
            List<ProductSearchDocument> snapshot = requireData(
                    productSource.loadProductDocument(internalToken, windowId, productId), "商品服务未返回菜品数据");
            if (snapshot.isEmpty()) {
                elasticsearch.delete(windowId + ":" + productId, MenuProductDocument.class);
            } else if (snapshot.size() == 1 && productId.equals(snapshot.getFirst().productId())
                    && windowId.equals(snapshot.getFirst().windowId())) {
                elasticsearch.save(toDocument(snapshot.getFirst()));
            } else {
                throw new IllegalStateException("商品服务返回了不匹配的菜品快照");
            }
            return null;
        }));
    }

    /**
     * 在影子索引写入完整快照，校验后切换别名。
     */
    public int rebuildAll() {
        return indexLock.withWriteLock(() -> {
            List<Long> windowIds = requireData(productSource.listSearchableWindowIds(internalToken), "商品服务未返回窗口列表");
            String newIndex = "menu-products-" + UUID.randomUUID().toString().replace("-", "");
            indexManager.createIndex(newIndex);
            IndexCoordinates target = IndexCoordinates.of(newIndex);
            long expected = 0;
            for (Long windowId : windowIds) {
                List<ProductSearchDocument> snapshot = requireData(
                        productSource.loadWindowDocuments(internalToken, windowId), "商品服务未返回窗口数据");
                List<MenuProductDocument> documents = snapshot.stream().map(this::toDocument).toList();
                if (!documents.isEmpty()) elasticsearch.save(documents, target);
                expected += documents.size();
            }
            elasticsearch.indexOps(target).refresh();
            long actual = elasticsearch.count(Query.findAll(), MenuProductDocument.class, target);
            if (expected != actual) throw new IllegalStateException("搜索索引重建数量不一致");
            // 切换期间写入事件等待读锁；切换后它们重新读取商品最新状态。
            indexManager.switchAlias(newIndex);
            return windowIds.size();
        });
    }

    @Override
    public int synchronizeExistingWindows() {
        if (elasticsearch.count(Query.findAll(), MenuProductDocument.class) > 0) return 0;
        return rebuildAll();
    }

    @Override
    public int reconcile() {
        return indexLock.withReadLock(() -> {
            List<Long> windowIds = requireData(productSource.listSearchableWindowIds(internalToken), "商品服务未返回窗口列表");
            Map<String, ProductSearchDocument> expected = new HashMap<>();
            for (Long windowId : windowIds) {
                for (ProductSearchDocument document : requireData(
                        productSource.loadWindowDocuments(internalToken, windowId), "商品服务未返回窗口数据")) {
                    String id = document.windowId() + ":" + document.productId();
                    if (expected.put(id, document) != null)
                        throw new IllegalStateException("商品快照存在重复菜品：" + id);
                }
            }
            Set<String> found = new HashSet<>();
            Map<String, long[]> repairs = new HashMap<>();
            Query all = Query.findAll();
            all.setPageable(PageRequest.of(0, 500));
            try (SearchHitsIterator<MenuProductDocument> hits = elasticsearch.searchForStream(all, MenuProductDocument.class)) {
                while (hits.hasNext()) {
                    MenuProductDocument actual = hits.next().getContent();
                    if (actual.getWindowId() == null || actual.getProductId() == null) {
                        throw new IllegalStateException("搜索索引存在缺少菜品或窗口 ID 的文档");
                    }
                    String id = actual.getWindowId() + ":" + actual.getProductId();
                    found.add(id);
                    ProductSearchDocument wanted = expected.get(id);
                    if (wanted == null || !sameDocument(actual, toDocument(wanted))) {
                        repairs.put(id, new long[]{actual.getWindowId(), actual.getProductId()});
                    }
                }
            }
            for (Map.Entry<String, ProductSearchDocument> entry : expected.entrySet()) {
                if (!found.contains(entry.getKey())) {
                    ProductSearchDocument document = entry.getValue();
                    repairs.put(entry.getKey(), new long[]{document.windowId(), document.productId()});
                }
            }
            // 快照可能已过期，修复时再次读取商品当前状态，避免覆盖并发变更。
            for (long[] repair : repairs.values()) refreshProduct(repair[0], repair[1]);
            return repairs.size();
        });
    }

    private boolean sameDocument(MenuProductDocument left, MenuProductDocument right) {
        return Objects.equals(left.getProductId(), right.getProductId())
                && Objects.equals(left.getWindowId(), right.getWindowId())
                && Objects.equals(left.getCategoryId(), right.getCategoryId())
                && Objects.equals(left.getRestaurantId(), right.getRestaurantId())
                && Objects.equals(left.getRestaurantName(), right.getRestaurantName())
                && Objects.equals(left.getRestaurantLocation(), right.getRestaurantLocation())
                && Objects.equals(left.getWindowName(), right.getWindowName())
                && Objects.equals(left.getCategoryName(), right.getCategoryName())
                && Objects.equals(left.getProductName(), right.getProductName())
                && Objects.equals(left.getDescription(), right.getDescription())
                && Objects.equals(left.getImageUrl(), right.getImageUrl())
                && Objects.equals(left.getSortOrder(), right.getSortOrder())
                && (left.getPrice() == null ? right.getPrice() == null
                : right.getPrice() != null && left.getPrice().compareTo(right.getPrice()) == 0);
    }

    private MenuProductDocument toDocument(ProductSearchDocument source) {
        MenuProductDocument d = new MenuProductDocument();
        d.setId(source.windowId() + ":" + source.productId());
        d.setProductId(source.productId());
        d.setCategoryId(source.categoryId());
        d.setWindowId(source.windowId());
        d.setRestaurantId(source.restaurantId());
        d.setRestaurantName(source.restaurantName());
        d.setRestaurantLocation(source.restaurantLocation());
        d.setWindowName(source.windowName());
        d.setCategoryName(source.categoryName());
        d.setProductName(source.productName());
        d.setDescription(source.description());
        d.setImageUrl(source.imageUrl());
        d.setPrice(source.price());
        d.setSortOrder(source.sortOrder());
        return d;
    }

    private ProductSearchResultVO toResult(MenuProductDocument document) {
        return new ProductSearchResultVO(document.getProductId(), document.getWindowId(), document.getRestaurantId(),
                document.getRestaurantName(), document.getRestaurantLocation(), document.getWindowName(),
                document.getCategoryName(), document.getProductName(), document.getDescription(),
                document.getImageUrl(), document.getPrice());
    }

    private <T> T requireData(ApiResponse<T> response, String message) {
        if (response == null || !CommonErrorCode.SUCCESS.code().equals(response.getCode()) || response.getData() == null) {
            throw new IllegalStateException(message);
        }
        return response.getData();
    }
}
