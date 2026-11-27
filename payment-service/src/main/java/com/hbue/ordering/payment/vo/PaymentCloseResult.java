package com.hbue.ordering.payment.vo;

import java.time.LocalDateTime;

/**
 * 支付服务向订单服务返回的内部关单结果。
 *
 * <p>当关单与成功支付竞争并最终确认 PAID 时，paidAt 必须来自 payments.success_time；
 * CLOSED 时 paidAt 为空。订单服务据此区分真正付款与普通关单，不从当前时间推算支付时刻。</p>
 */
public record PaymentCloseResult(String status, LocalDateTime paidAt) {
}
