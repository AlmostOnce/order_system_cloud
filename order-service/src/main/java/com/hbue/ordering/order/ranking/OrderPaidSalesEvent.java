package com.hbue.ordering.order.ranking;

/** 不携带用户或支付密码；消费者从订单库核实购买事实。 */
public record OrderPaidSalesEvent(int version, String eventId, Long orderId) {
    public static OrderPaidSalesEvent of(Long orderId) {
        return new OrderPaidSalesEvent(1, "order-paid-sales:" + orderId, orderId);
    }
    public boolean valid() {
        return version == 1 && orderId != null && orderId > 0
                && ("order-paid-sales:" + orderId).equals(eventId);
    }
}
