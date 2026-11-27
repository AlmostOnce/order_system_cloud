package com.hbue.ordering.recommendation.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.product.api.dto.ProductRecommendationDocument;
import com.hbue.ordering.product.api.dto.ProductRecommendationValidationRequest;
import com.hbue.ordering.product.api.recommendationclient.ProductRecommendationSourceClient;
import com.hbue.ordering.recommendation.mapper.*;
import com.hbue.ordering.recommendation.model.RecommendationProductFeatureDO;
import com.hbue.ordering.recommendation.service.RecommendationAlgorithm;
import com.hbue.ordering.recommendation.service.RecommendationQueryService;
import com.hbue.ordering.recommendation.vo.RecommendationHomeVO;
import com.hbue.ordering.stock.api.client.StockServiceClient;
import com.hbue.ordering.stock.api.dto.StockBatchQueryRequest;
import com.hbue.ordering.stock.api.vo.StockVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 首页推荐查询编排；先读本地投影排序，再批量复核商品当前展示事实。
 */
@Service
public class RecommendationQueryServiceImpl implements RecommendationQueryService {
    private static final int MAX_CANDIDATES = 2000;
    private static final int MAX_PROFILE_ORDERS = 500;
    private static final int MAX_RANKED_BEFORE_VALIDATION = 50;
    private final RecommendationProductFeatureMapper products;
    private final RecommendationOrderSnapshotMapper orders;
    private final RecommendationOrderItemMapper orderItems;
    private final RecommendationFavoriteStateMapper favorites;
    private final RecommendationSearchIntentMapper searches;
    private final RecommendationHotProductMapper hotProducts;
    private final RecommendationAlgorithm algorithm;
    private final ProductRecommendationSourceClient productSource;
    private final StockServiceClient stockSource;
    private final String internalToken;

    /**
     * 注入只读推荐投影、确定性算法和唯一商品事实复核契约。
     */
    public RecommendationQueryServiceImpl(RecommendationProductFeatureMapper products,
                                          RecommendationOrderSnapshotMapper orders, RecommendationOrderItemMapper orderItems,
                                          RecommendationFavoriteStateMapper favorites, RecommendationSearchIntentMapper searches,
                                          RecommendationHotProductMapper hotProducts, RecommendationAlgorithm algorithm,
                                          ProductRecommendationSourceClient productSource, StockServiceClient stockSource,
                                          @Value("${internal.scope.token:}") String internalToken) {
        this.products = products;
        this.orders = orders;
        this.orderItems = orderItems;
        this.favorites = favorites;
        this.searches = searches;
        this.hotProducts = hotProducts;
        this.algorithm = algorithm;
        this.productSource = productSource;
        this.stockSource = stockSource;
        this.internalToken = internalToken;
    }

    /**
     * 只查询已解析的本人内部编号；所有行为查询都按该用户收窄，最终商品信息来自商品服务批量复核。
     * 数据依赖失败会向上抛出，不伪装成该用户没有行为；商品服务无法确认时安全返回空降级列表。
     */
    @Override
    public RecommendationHomeVO recommend(Long userId, Long restaurantId, int limit) {
        if (userId == null || userId <= 0 || restaurantId != null && restaurantId <= 0 || limit < 1 || limit > 10) {
            throw new IllegalArgumentException("首页推荐查询范围无效");
        }
        LocalDateTime now = LocalDateTime.now();
        List<RecommendationProductFeatureDO> candidates = products.selectAvailable(restaurantId, MAX_CANDIDATES);
        if (candidates.isEmpty()) return response("COLD_START", List.of());

        List<com.hbue.ordering.recommendation.model.RecommendationOrderSnapshotDO> userOrders =
                orders.selectRecentByUser(userId, now.minusDays(90), MAX_PROFILE_ORDERS);
        List<Long> orderIds = userOrders.stream().map(order -> order.getOrderId()).toList();
        List<com.hbue.ordering.recommendation.model.RecommendationOrderItemDO> lines = orderIds.isEmpty()
                ? List.of() : orderItems.selectByOrderIds(orderIds);
        List<com.hbue.ordering.recommendation.model.RecommendationFavoriteStateDO> userFavorites =
                favorites.selectActiveByUser(userId);
        List<com.hbue.ordering.recommendation.model.RecommendationSearchIntentDO> userSearches =
                searches.selectRecentByUser(userId, now.minusDays(14), 20);
        List<com.hbue.ordering.recommendation.model.RecommendationHotProductDO> hot = hotProducts.selectTop(50);

        Set<Long> historyIds = new HashSet<>();
        for (var line : lines) historyIds.add(line.getProductId());
        for (var favorite : userFavorites) {
            if ("PRODUCT".equals(favorite.getTargetType())) historyIds.add(favorite.getTargetId());
        }
        candidates.forEach(item -> historyIds.remove(item.getProductId()));
        List<RecommendationProductFeatureDO> historyFeatures = historyIds.isEmpty()
                ? List.of() : products.selectByProductIds(new ArrayList<>(historyIds));
        RecommendationAlgorithm.RankedResult ranked = algorithm.rank(userId, restaurantId,
                MAX_RANKED_BEFORE_VALIDATION, candidates, historyFeatures, userOrders, lines,
                userFavorites, userSearches, hot, now);
        if (ranked.items().isEmpty()) return response(ranked.mode(), List.of());

        List<RecommendationAlgorithm.RankedItem> toValidate = ranked.items().stream()
                .limit(MAX_RANKED_BEFORE_VALIDATION).toList();
        ProductRecommendationValidationRequest validation = new ProductRecommendationValidationRequest(
                toValidate.stream().map(item -> new ProductRecommendationValidationRequest.Item(
                        item.product().getProductId(), item.product().getWindowId())).toList());
        ApiResponse<List<ProductRecommendationDocument>> validated;
        try {
            validated = productSource.validateCandidates(internalToken, validation);
        } catch (RuntimeException unavailable) {
            // 商品状态无法重新确认时不展示可能已下架的旧快照；其他首页目录仍由 uni-app 独立加载。
            return response("FALLBACK", List.of());
        }
        if (validated == null || !CommonErrorCode.SUCCESS.code().equals(validated.getCode())
                || validated.getData() == null) return response("FALLBACK", List.of());

        ApiResponse<List<StockVO>> stockResponse;
        try {
            StockBatchQueryRequest stockRequest = new StockBatchQueryRequest(validated.getData().stream()
                    .map(item -> new StockBatchQueryRequest.Item(item.windowId(), item.productId())).toList());
            stockResponse = stockSource.getBatchStocks(internalToken, stockRequest);
        } catch (RuntimeException unavailable) {
            // 库存事实无法确认时不展示潜在缺货项，也不把服务降级包装成零库存成功响应。
            return response("FALLBACK", List.of());
        }
        if (stockResponse == null || !CommonErrorCode.SUCCESS.code().equals(stockResponse.getCode())
                || stockResponse.getData() == null) return response("FALLBACK", List.of());

        Map<Long, ProductRecommendationDocument> current = new HashMap<>();
        for (ProductRecommendationDocument item : validated.getData()) current.put(item.productId(), item);
        Set<String> availableStock = new HashSet<>();
        for (StockVO stock : stockResponse.getData()) {
            if (stock != null && stock.getWindowId() != null && stock.getProductId() != null
                    && stock.getAvailableQty() != null && stock.getAvailableQty() > 0) {
                availableStock.add(stock.getWindowId() + ":" + stock.getProductId());
            }
        }
        List<RecommendationHomeVO.Item> responseItems = new ArrayList<>(limit);
        for (RecommendationAlgorithm.RankedItem item : toValidate) {
            ProductRecommendationDocument document = current.get(item.product().getProductId());
            if (document == null || !item.product().getWindowId().equals(document.windowId())
                    || restaurantId != null && !restaurantId.equals(document.restaurantId())
                    || !availableStock.contains(document.windowId() + ":" + document.productId())) continue;
            responseItems.add(new RecommendationHomeVO.Item(document.productId(), document.windowId(),
                    document.restaurantId(), document.productName(), document.imageUrl(), document.basePrice(),
                    document.hasSpecifications(), document.hasSpecifications() ? "STARTING" : "FIXED", item.reasonCode()));
            if (responseItems.size() == limit) break;
        }
        return response(ranked.mode(), responseItems);
    }

    /**
     * 每次查询生成关联 ID 供页面/后续曝光统计关联，不携带内部用户或原始评分。
     */
    private RecommendationHomeVO response(String mode, List<RecommendationHomeVO.Item> items) {
        return new RecommendationHomeVO(UUID.randomUUID().toString(), RecommendationAlgorithm.ALGORITHM_VERSION,
                mode, LocalDateTime.now(), List.copyOf(items));
    }
}
