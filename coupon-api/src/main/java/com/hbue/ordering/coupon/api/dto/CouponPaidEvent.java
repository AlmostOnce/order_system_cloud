package com.hbue.ordering.coupon.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 订单服务在确认真实支付后经 Outbox 发布的券核销事件。
 *
 * <p>事件只有订单号和券记录 ID，不包含用户提交金额；券服务收到后须核对该券仍绑定
 * 此订单且处于 LOCKED。重复投递按 eventId 和订单绑定幂等，异常时由消息重试恢复。</p>
 *
 * @param eventId 订单侧 Outbox 事件唯一标识
 * @param orderNo 已确认支付的订单号
 * @param userCouponId 下单时已锁定的用户券 ID
 */
public record CouponPaidEvent(
        @NotBlank String eventId,
        @NotBlank String orderNo,
        @NotNull Long userCouponId
) {
}
