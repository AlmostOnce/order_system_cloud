package com.hbue.ordering.order.api.recommendation;

import java.util.List;

/**
 * 推荐服务历史购买回填的一页权威快照，仅供内部凭据认证后的系统调用。
 * 不返回总数、不支持页码；下一游标来自本页最后一个实际返回的订单，避免跳过未返回事实。
 *
 * @param items 按orderId升序的完整订单快照，与实时事件共用version=1协议
 * @param nextAfterOrderId 确有下一页时返回本页末订单ID，否则为null
 * @param hasMore 是否通过额外一条记录确认还有后续订单
 */
public record RecommendationPurchaseSnapshotPageVO(List<RecommendationPurchaseSnapshotEvent> items,
        Long nextAfterOrderId, boolean hasMore) { }
