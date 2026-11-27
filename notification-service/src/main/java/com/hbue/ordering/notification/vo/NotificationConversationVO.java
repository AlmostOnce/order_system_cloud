package com.hbue.ordering.notification.vo;

import java.time.LocalDateTime;

/**
 * 系统通知会话摘要。
 *
 * <p>消息页按会话展示通知，而不是直接铺开通知列表：会话是否存在由后端根据
 * 是否产生过通知决定，前端不再硬编码空会话。当前所有系统通知归入同一个
 * 「订单中心」会话，未读数与消息中心红点口径一致。</p>
 *
 * @param conversationId   会话稳定标识，前端据此区系统会话与商家会话
 * @param conversationType 会话类型，当前固定为 SYSTEM
 * @param title            会话名称
 * @param preview          最新一条通知正文摘要
 * @param latestAt         最新通知时间，用于会话排序与展示
 * @param unreadCount      该会话未读数，与红点使用同一统计口径
 */
public record NotificationConversationVO(
        String conversationId,
        String conversationType,
        String title,
        String preview,
        LocalDateTime latestAt,
        long unreadCount
) {
}
