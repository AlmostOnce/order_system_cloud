package com.hbue.ordering.notification.dto;

/**
 * 支持工单状态变更站内信内部请求体。
 *
 * <p>由反馈服务在受理或关闭工单的事务提交后调用。{@code eventId} 取本次追加的
 * 系统时间线消息公开标识，与接收人共同构成通知表唯一约束，调用方网络重试
 * 不会产生重复站内信。</p>
 *
 * @param recipientId 接收学生内部用户编号
 * @param eventId     来源事件标识，取系统时间线消息标识
 * @param title       通知标题
 * @param content     通知正文
 * @param ticketId    公开工单标识，写入 biz_id 供消息中心跳转反馈详情
 * @author order-system
 * @date 2026-11-23
 */
public record SupportStatusNotificationRequest(
        Long recipientId,
        String eventId,
        String title,
        String content,
        String ticketId
) {
}
