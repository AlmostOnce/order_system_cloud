package com.hbue.ordering.recommendation.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.product.api.dto.ProductRecommendationDocument;
import com.hbue.ordering.product.api.recommendationclient.ProductRecommendationSourceClient;
import com.hbue.ordering.recommendation.service.RecommendationProductCatalogService;
import com.hbue.ordering.recommendation.service.RecommendationProductFeatureWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.List;

/** 从商品服务刷新本地商品特征；商品服务不可用时保留上一份完整快照。 */
@Service
public class RecommendationProductCatalogServiceImpl implements RecommendationProductCatalogService {
    private final ProductRecommendationSourceClient source;
    private final RecommendationProductFeatureWriter writer;
    private final String internalToken;

    /** 注入受保护商品契约及本地原子投影写入器。 */
    public RecommendationProductCatalogServiceImpl(ProductRecommendationSourceClient source,
            RecommendationProductFeatureWriter writer,
            @Value("${internal.scope.token:}") String internalToken) {
        this.source = source;
        this.writer = writer;
        this.internalToken = internalToken;
    }

    /** 读取所有当前有效窗口编号；任何上游错误都抛出，不能把读取失败解释成空目录。 */
    @Override
    public List<Long> windowIds() {
        ApiResponse<List<Long>> response = source.listWindowIds(internalToken);
        if (!success(response) || response.getData() == null) throw new IllegalStateException("商品窗口目录暂不可用");
        return response.getData();
    }

    /** 一次读取并原子替换一个窗口全部推荐商品。 */
    @Override
    public void refreshWindow(Long windowId) {
        ApiResponse<List<ProductRecommendationDocument>> response = source.loadWindowDocuments(internalToken, windowId);
        if (!success(response) || response.getData() == null) throw new IllegalStateException("商品窗口快照暂不可用");
        writer.replaceWindow(windowId, response.getData());
    }

    /** 读取单个商品当前快照；源端空数组会撤下该菜的旧推荐展示状态。 */
    @Override
    public void refreshProduct(Long windowId, Long productId) {
        ApiResponse<List<ProductRecommendationDocument>> response = source.loadProductDocument(
                internalToken, windowId, productId);
        if (!success(response) || response.getData() == null) throw new IllegalStateException("商品快照暂不可用");
        writer.replaceProduct(windowId, productId, response.getData());
    }

    /** 以商品服务成功返回的完整窗口集合为准，撤下已不再公开的窗口候选。 */
    @Override
    public void markWindowsMissing(List<Long> activeWindowIds) {
        writer.markWindowsMissing(activeWindowIds);
    }

    /** 仅接受项目标准成功码，防止封装了错误码的HTTP 200被当成有效空数据。 */
    private boolean success(ApiResponse<?> response) {
        return response != null && CommonErrorCode.SUCCESS.code().equals(response.getCode());
    }
}
