package com.hbue.ordering.product.service;

import com.hbue.ordering.product.api.dto.ProductRecommendationDocument;
import com.hbue.ordering.product.api.dto.ProductRecommendationValidationRequest;

import java.util.List;

/** 商品域为推荐服务提供的当前有效目录读取边界。 */
public interface ProductRecommendationSourceService {
    /** 返回当前可用窗口编号；窗口归属和营业状态由商品数据库决定。 */
    List<Long> windowIds();

    /** 一次读取窗口内所有有效菜品、分类及启用规格，避免每个推荐卡片单独查商品。 */
    List<ProductRecommendationDocument> documents(Long windowId);

    /** 读取一个商品当前快照；无结果代表其当前不应再作为首页候选。 */
    List<ProductRecommendationDocument> document(Long windowId, Long productId);

    /** 按窗口分组复核最多50个已排序候选，避免逐菜请求并返回最新基础价和规格概况。 */
    List<ProductRecommendationDocument> validateCandidates(ProductRecommendationValidationRequest request);
}
