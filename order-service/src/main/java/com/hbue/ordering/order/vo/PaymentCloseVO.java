package com.hbue.ordering.order.vo;

import java.time.LocalDateTime;

/**
 * 支付服务关单接口的内部响应。
 *
 * <p>status 为 PAID 时必须携带支付库已持久化的 paidAt；订单服务只有拿到这份真实时间才会推进支付状态，
 * 防止自动取消与迟到支付竞争时用本地当前时间伪造支付月份。</p>
 */
public record PaymentCloseVO(String status, LocalDateTime paidAt) {
}
