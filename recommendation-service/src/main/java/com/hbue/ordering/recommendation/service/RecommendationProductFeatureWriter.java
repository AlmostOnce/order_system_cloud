package com.hbue.ordering.recommendation.service;

import com.hbue.ordering.product.api.dto.ProductRecommendationDocument;
import java.util.List;

/** 将商品服务确认的完整展示事实事务性写入推荐商品读模型。 */
public interface RecommendationProductFeatureWriter {
    /**
     * 原子替换一个窗口的完整商品快照并撤下上游未返回的旧商品。
     *
     * @param windowId 当前刷新窗口
     * @param documents 商品服务在一次成功读取中返回的完整窗口商品列表，空列表表示该窗口无有效商品
     */
    void replaceWindow(Long windowId, List<ProductRecommendationDocument> documents);

    /** 只更新或撤下指定菜品，不影响同窗口其他目录项。
     * @param windowId 商品所在窗口
     * @param productId 本次刷新的菜品主键
     * @param documents 商品服务返回的零或一条权威快照
     */
    void replaceProduct(Long windowId, Long productId, List<ProductRecommendationDocument> documents);

    /**
     * 源服务完整窗口清单成功后，撤下已失效窗口中的候选。
     * @param activeWindowIds 商品服务当前确认有效的窗口编号列表
     */
    void markWindowsMissing(List<Long> activeWindowIds);
}
