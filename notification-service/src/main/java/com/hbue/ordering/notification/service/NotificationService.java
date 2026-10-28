package com.hbue.ordering.notification.service;

import com.hbue.ordering.notification.vo.NotificationPageVO;

public interface NotificationService {
    NotificationPageVO page(long userId, String cursor, int limit);
    long unreadCount(long userId);
    void markRead(long userId, String notificationId);
    void markAllRead(long userId);
}
