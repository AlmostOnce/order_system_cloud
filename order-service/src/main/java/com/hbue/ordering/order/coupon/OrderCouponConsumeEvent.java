package com.hbue.ordering.order.coupon;

/**
 * 订单支付成功后交给优惠券服务核销的最小事实事件。
 *
 * <p>事件只携带稳定事件号、订单号和已锁定用户券 ID；券服务据订单号和券 ID 校验其自身状态，
 * 不依赖订单库跨库查询，也不接收客户端提供的金额或身份信息。</p>
 *
 * <p>放在 coupon 特性包内与 Outbox 写入器、发布器同包，使"核销事件的生产与发布"成为一个内聚单元，
 * 不再散落到通用 service 包。</p>
 *
 * @param eventId      以订单号确定的稳定幂等事件 ID
 * @param orderNo      已成功支付的订单业务编号
 * @param userCouponId 订单已锁定并绑定的用户券记录 ID
 * @author order-system
 * @date 2026-10-24
 */
public record OrderCouponConsumeEvent(String eventId, String orderNo, Long userCouponId) {

    /**
     * 事件号固定前缀；与 {@link #valid()} 的长度校验共用。
     */
    private static final String EVENT_PREFIX = "order-coupon-paid:";

    /**
     * 从订单库已提交的券绑定事实生成事件；事件号在所有重试中保持不变。
     *
     * @param orderNo      订单服务生成的业务订单号
     * @param userCouponId 下单时锁定并绑定到订单的用户券 ID
     * @return 可序列化的核销消息
     */
    public static OrderCouponConsumeEvent of(String orderNo, Long userCouponId) {
        return new OrderCouponConsumeEvent(EVENT_PREFIX + orderNo, orderNo, userCouponId);
    }

    /**
     * 校验消息字段及稳定事件号，拒绝损坏或错误关联的 Outbox 载荷。
     *
     * @return 字段完整且事件号可由订单号重算得出时返回 true
     */
    public boolean valid() {
        // event_id 列长为 64，固定前缀占 18 个字符，因此订单号最多只能占 46 个字符。
        return orderNo != null && !orderNo.isBlank() && orderNo.length() <= 46
                && userCouponId != null && userCouponId > 0
                && (EVENT_PREFIX + orderNo).equals(eventId);
    }
}
