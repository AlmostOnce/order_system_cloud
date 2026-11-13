package com.hbue.ordering.order.api.dto;

import java.time.LocalDateTime;

/**
 * 支付服务通知订单服务支付已确认的内部命令。
 *
 * @param paidAt 支付服务 payments.success_time 中已持久化的真实成功时间；不得使用客户端时间或订单更新时间替代
 */
public record OrderPaidCommand(LocalDateTime paidAt) {
}
