package com.hbue.ordering.support.client.vo;

/**
 * 通知服务支持回复事件请求。
 *
 * <p>由反馈服务在商家公开回复事务提交后调用。{@code eventId} 使用回复消息的公开
 * 标识，通知服务据此幂等落库，网络重试不会产生重复站内信。</p>
 *
 * @param recipientId 接收学生内部用户编号
 * @param eventId     幂等事件标识，取回复消息标识
 * @param title       站内信标题
 * @param content     站内信正文
 * @param ticketId    公开工单标识，通知服务写入业务引用供消息中心跳转
 * @author order-system
 * @date 2026-11-23
 */
public record SupportReplyNotificationRequest(
        Long recipientId,
        String eventId,
        String title,
        String content,
        String ticketId
) {
}
