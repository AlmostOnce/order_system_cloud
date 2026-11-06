package com.hbue.ordering.notification.service;

import com.hbue.ordering.notification.vo.NotificationPageVO;

/**
 * 本人通知查询与阅读契约。接口只操作认证用户的通知，不提供跨用户批量已读入口。
 */
public interface NotificationService {
    /**
     * 按当前用户可访问范围查询游标页。游标属于其他用户时拒绝；额外取一条判断下一页，避免加载全部记录。
     */
    NotificationPageVO page(long userId, String cursor, int limit);
    /**
     * 查询当前用户尚未阅读的系统通知数，与商家聊天未读独立存储，前端合并展示红点。
     */
    long unreadCount(long userId);
    /**
     * 确认通知属于本人后标记已读；不存在与他人通知统一拒绝，不让客户端指定接收人。
     */
    void markRead(long userId, String notificationId);
    /**
     * 只更新当前认证用户的全部系统通知，不改变商家聊天的独立阅读水位。
     */
    void markAllRead(long userId);
}
