package com.hbue.ordering.order.notification;

/** 状态变更事件；订单版本让重复消息具有稳定的唯一标识。 */
public record OrderNotificationEvent(int version, String eventId, Long orderId, Integer orderVersion,
        Long recipientId, String status) {
    public static OrderNotificationEvent of(Long orderId, Integer orderVersion, Long recipientId, String status) {
        return new OrderNotificationEvent(1, "order-status:" + orderId + ":" + orderVersion,
                orderId, orderVersion, recipientId, status);
    }
}
