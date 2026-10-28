package com.hbue.ordering.notification.messaging;

import java.util.Set;

/** 订单服务提交的状态变化事件；字段仅供服务间传递。 */
public record OrderNotificationEvent(int version, String eventId, Long orderId, Integer orderVersion,
        Long recipientId, String status) {
    private static final Set<String> ALLOWED = Set.of("已支付", "制作中", "待取货", "已完成", "已取消");
    public boolean valid() {
        return version == 1 && orderId != null && orderId > 0 && orderVersion != null && orderVersion > 0
                && recipientId != null && recipientId > 0 && status != null && ALLOWED.contains(status)
                && ("order-status:" + orderId + ":" + orderVersion).equals(eventId);
    }
}
