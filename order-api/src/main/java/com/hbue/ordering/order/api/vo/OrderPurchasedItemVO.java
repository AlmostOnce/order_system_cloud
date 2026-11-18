package com.hbue.ordering.order.api.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 订单详情明细的只读投影，仅保留购物车清理需要的购买事实。
 * 名称、金额等额外字段仍由订单详情接口提供，本契约不参与重新核价。
 *
 * @param productId    实际购买的商品 ID
 * @param quantity     订单保存的购买数量
 * @param selectionKey 订单保存的规格组合键；旧调用方未提供时按 base 兼容
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderPurchasedItemVO(Long productId, Integer quantity, String selectionKey) {
    /**
     * 保持旧调用方可用；无规格菜品使用稳定的 base 组合键。
     */
    public OrderPurchasedItemVO(Long productId, Integer quantity) {
        this(productId, quantity, "base");
    }

    /**
     * 兼容不包含规格字段的旧版订单响应。
     */
    public OrderPurchasedItemVO {
        if (selectionKey == null || selectionKey.isBlank()) {
            selectionKey = "base";
        }
    }
}
