package com.hbue.ordering.product.api.dto;

import java.util.List;

/**
 * 推荐完成排序后请求商品域批量复核的菜品范围，价格与规格仍由商品服务返回。
 */
public record ProductRecommendationValidationRequest(List<Item> items) {
    /**
     * 菜品及当前所属窗口；两个编号均由推荐服务本地商品目录取得。
     */
    public record Item(Long productId, Long windowId) {
    }
}
