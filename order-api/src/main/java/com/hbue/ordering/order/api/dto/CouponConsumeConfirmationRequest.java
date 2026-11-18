package com.hbue.ordering.order.api.dto;

/**
 * 优惠券服务在数据库核销提交后确认订单 Outbox 事件已消费。
 *
 * @param eventId      与订单 Outbox 及消息体一致的事件 ID
 * @param orderNo      已支付订单的业务订单号
 * @param userCouponId 被订单锁定并由券服务核销的用户券 ID
 */
public record CouponConsumeConfirmationRequest(String eventId, String orderNo, Long userCouponId) {
}
