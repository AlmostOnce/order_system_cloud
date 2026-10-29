package com.hbue.ordering.notification.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.notification.mapper.NotificationMapper;
import com.hbue.ordering.notification.vo.NotificationVO;
import com.hbue.ordering.notification.service.NotificationService;
import com.hbue.ordering.notification.vo.NotificationPageVO;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class NotificationServiceImpl implements NotificationService {
    private static final Pattern UUID = Pattern.compile("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");
    private final NotificationMapper repository;
    public NotificationServiceImpl(NotificationMapper repository) { this.repository = repository; }

    public NotificationPageVO page(long userId, String cursor, int limit) {
        if (userId <= 0 || limit < 1 || limit > 50 || (cursor != null && !UUID.matcher(cursor).matches()))
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "分页参数不正确");
        if (cursor != null && !repository.existsForUser(userId, cursor))
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "通知不存在");
        var rows = repository.page(userId, cursor, limit + 1);
        boolean hasMore = rows.size() > limit;
        var items = rows.stream().limit(limit).map(row -> new NotificationVO(row.getNotificationId(), row.getNotificationType(), row.getTitle(), row.getContent(), row.getOrderId(), row.getCreatedAt(), row.getReadAt())).toList();
        return new NotificationPageVO(items, hasMore ? items.getLast().notificationId() : null, hasMore);
    }
    public long unreadCount(long userId) { return repository.unreadCount(userId); }
    public void markRead(long userId, String id) {
        if (id == null || !UUID.matcher(id).matches()) throw new BusinessException(CommonErrorCode.BAD_REQUEST, "通知标识不正确");
        if (!repository.existsForUser(userId, id)) throw new BusinessException(CommonErrorCode.NOT_FOUND, "通知不存在");
        repository.markRead(userId, id);
    }
    public void markAllRead(long userId) { repository.markAllRead(userId); }
}
