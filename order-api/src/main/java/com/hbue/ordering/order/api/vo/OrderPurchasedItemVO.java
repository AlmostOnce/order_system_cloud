package com.hbue.ordering.order.api.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 订单详情明细的只读投影，仅保留购物车清理需要的购买事实。
 * 名称、金额等额外字段仍由订单详情接口提供，本契约不参与重新核价。
 *
 * @param productId 实际购买的商品 ID
 * @param quantity 订单保存的购买数量
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderPurchasedItemVO(Long productId, Integer quantity) {
}
