package com.hbue.ordering.order.service;

/**
 * 订单支付成功后交给优惠券服务核销的最小事实事件。
 *
 * <p>事件只携带稳定事件号、订单号和已锁定用户券 ID；券服务据订单号和券 ID 校验其自身状态，
 * 不依赖订单库跨库查询，也不接收客户端提供的金额或身份信息。</p>
 *
 * @param eventId      以订单号确定的稳定幂等事件 ID
 * @param orderNo      已成功支付的订单业务编号
 * @param userCouponId 订单已锁定并绑定的用户券记录 ID
 */
public record OrderCouponConsumeEvent(String eventId, String orderNo, Long userCouponId) {

    /**
     * 从订单库已提交的券绑定事实生成事件；事件号在所有重试中保持不变。
     *
     * @param orderNo      订单服务生成的业务订单号
     * @param userCouponId 下单时锁定并绑定到订单的用户券 ID
     * @return 可序列化的核销消息
     */
    public static OrderCouponConsumeEvent of(String orderNo, Long userCouponId) {
        return new OrderCouponConsumeEvent("order-coupon-paid:" + orderNo, orderNo, userCouponId);
    }

    /**
     * 校验消息字段及稳定事件号，拒绝损坏或错误关联的 Outbox 载荷。
     */
    public boolean valid() {
        // event_id 列长为 64，固定前缀占 18 个字符，因此订单号最多只能占 46 个字符。
        return orderNo != null && !orderNo.isBlank() && orderNo.length() <= 46
                && userCouponId != null && userCouponId > 0
                && ("order-coupon-paid:" + orderNo).equals(eventId);
    }
}
