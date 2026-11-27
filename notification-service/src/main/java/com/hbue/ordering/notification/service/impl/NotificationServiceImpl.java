package com.hbue.ordering.notification.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.notification.mapper.NotificationMapper;
import com.hbue.ordering.notification.service.NotificationService;
import com.hbue.ordering.notification.service.SystemConversationService;
import com.hbue.ordering.notification.vo.NotificationConversationVO;
import com.hbue.ordering.notification.vo.NotificationPageVO;
import com.hbue.ordering.notification.vo.NotificationVO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 系统订单通知查询和已读服务。分页游标必须属于本人，不存在与越权保持同样的资源不可见语义。
 */
@Service
public class NotificationServiceImpl implements NotificationService {
    private static final Pattern UUID = Pattern.compile("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");
    private final NotificationMapper repository;

    /**
     * 系统会话业务：订单中心等系统会话是持久化实体，由独立服务维护创建与摘要推进。
     */
    private final SystemConversationService systemConversations;

    /**
     * 注入系统通知Mapper与会话服务，所有读写都由业务方法绑定当前认证用户范围。
     */
    public NotificationServiceImpl(NotificationMapper repository, SystemConversationService systemConversations) {
        this.repository = repository;
        this.systemConversations = systemConversations;
    }

    /**
     * 按当前用户可访问范围查询游标页。游标属于其他用户时拒绝；额外取一条判断下一页，避免加载全部记录。
     */
    public NotificationPageVO page(long userId, String cursor, int limit) {
        if (userId <= 0 || limit < 1 || limit > 50 || (cursor != null && !UUID.matcher(cursor).matches()))
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "分页参数不正确");
        if (cursor != null && !repository.existsForUser(userId, cursor))
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "通知不存在");
        var rows = repository.page(userId, cursor, limit + 1);
        boolean hasMore = rows.size() > limit;
        var items = rows.stream().limit(limit).map(row -> new NotificationVO(row.getNotificationId(), row.getNotificationType(), row.getTitle(), row.getContent(), row.getOrderId(), row.getBizType(), row.getBizId(), row.getCreatedAt(), row.getReadAt())).toList();
        return new NotificationPageVO(items, hasMore ? items.getLast().notificationId() : null, hasMore);
    }

    /**
     * 查询当前用户尚未阅读的系统通知数，与商家聊天未读独立存储，前端合并展示红点。
     */
    public long unreadCount(long userId) {
        return repository.unreadCount(userId);
    }

    /**
     * 查询当前用户的系统通知会话。
     *
     * <p>系统通知目前归入唯一的「订单中心」会话：有历史通知才返回，从未收到
     * 通知时返回空列表，由前端展示统一空态，不再伪造空会话。会话未读数复用
     * 红点统计口径，被通知偏好关闭的分类不计入角标。</p>
     *
     * @param userId 当前认证用户内部编号
     * @return 会话列表；没有历史通知时为空
     */
    public List<NotificationConversationVO> conversations(long userId) {
        // 会话由持久化实体提供：订单中心常驻，历史通知在会话首次读取时回填摘要。
        return systemConversations.list(userId);
    }

    /**
     * 确认通知属于本人后标记已读；不存在与他人通知统一拒绝，不让客户端指定接收人。
     */
    public void markRead(long userId, String id) {
        if (id == null || !UUID.matcher(id).matches())
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "通知标识不正确");
        if (!repository.existsForUser(userId, id)) throw new BusinessException(CommonErrorCode.NOT_FOUND, "通知不存在");
        repository.markRead(userId, id);
    }

    /**
     * 只更新当前认证用户的全部系统通知，不改变商家聊天的独立阅读水位。
     */
    public void markAllRead(long userId) {
        repository.markAllRead(userId);
    }
}
