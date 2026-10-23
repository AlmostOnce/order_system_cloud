package com.hbue.ordering.order.api.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * 已有 GET /orders/{orderId} 详情响应的只读字段投影。
 * 不把订单服务的 DO 或实现模块暴露给调用者；额外展示字段允许向后兼容扩展。
 *
 * @param orderId 实际订单 ID
 * @param windowId 订单所属窗口
 * @param items 已持久化的购买明细，不是当前商品目录
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderCartSnapshotVO(Long orderId, Long windowId,
                                  List<OrderPurchasedItemVO> items) {
}
