package com.hbue.ordering.notification.service.impl;

import com.hbue.ordering.notification.mapper.NotificationMapper;
import com.hbue.ordering.notification.mapper.SystemConversationMapper;
import com.hbue.ordering.notification.service.SystemConversationService;
import com.hbue.ordering.notification.vo.NotificationConversationVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 系统通知会话实现。
 *
 * <p>订单中心是常驻会话：用户首次打开消息页时创建，即使从未收到通知也会返回，
 * 与微信服务号的会话模型一致。升级前已有通知但没有会话行的老用户，
 * 用通知表最新一条回填摘要，避免看到空会话。</p>
 *
 * <p>未读数继续复用通知红点口径（受通知偏好影响），会话实体本身不冗余未读数，
 * 避免“单条已读”与会话计数出现双写不一致。</p>
 *
 * @author order-system
 * @date 2026-11-26
 */
@Service
public class SystemConversationServiceImpl implements SystemConversationService {

    /**
     * 订单中心会话类型。
     */
    static final String TYPE_ORDER = "ORDER";

    /**
     * 订单中心会话名称。
     */
    static final String TITLE_ORDER = "订单中心";

    /**
     * 订单中心对外的稳定会话标识，与商家会话 UUID 区分。
     */
    static final String CONVERSATION_ID_ORDER = "system-order";

    /**
     * 系统会话大类，前端用于区分系统会话与商家会话。
     */
    private static final String TYPE_SYSTEM = "SYSTEM";

    /**
     * 系统会话数据访问。
     */
    private final SystemConversationMapper conversations;

    /**
     * 通知数据访问，用于回填历史摘要与统计未读数。
     */
    private final NotificationMapper notifications;

    /**
     * 创建系统会话服务。
     *
     * @param conversations 系统会话数据访问
     * @param notifications 通知数据访问
     */
    public SystemConversationServiceImpl(SystemConversationMapper conversations, NotificationMapper notifications) {
        this.conversations = conversations;
        this.notifications = notifications;
    }

    @Override
    @Transactional
    public void advance(long customerId, String conversationType, String title, String preview, LocalDateTime latestAt) {
        // 会话类型是唯一键的一部分，缺失时无法定位会话；直接忽略而不是写入脏数据。
        if (customerId <= 0 || conversationType == null || conversationType.isBlank()) return;
        conversations.advance(customerId, conversationType, title, preview == null ? "" : preview, latestAt);
    }

    @Override
    @Transactional
    public List<NotificationConversationVO> list(long customerId) {
        if (customerId <= 0) return List.of();
        // 订单中心必须在首次访问时创建：会话一旦存在便常驻，不因已读或无新消息消失。
        conversations.ensure(customerId, TYPE_ORDER, TITLE_ORDER);
        backfillOrder(customerId);
        long unread = notifications.unreadCount(customerId);
        var rows = conversations.listByCustomer(customerId);
        var items = new ArrayList<NotificationConversationVO>(rows.size());
        for (var row : rows) {
            // 当前只有订单中心承载系统通知；其它类型后续接入时再补各自的未读统计口径。
            long count = TYPE_ORDER.equals(row.getConversationType()) ? unread : 0L;
            items.add(new NotificationConversationVO(
                    conversationId(row.getConversationType()),
                    TYPE_SYSTEM,
                    row.getTitle(),
                    row.getPreview(),
                    row.getLatestAt(),
                    count
            ));
        }
        return items;
    }

    /**
     * 回填订单中心的历史摘要。
     *
     * <p>仅在会话尚无通知时间时执行一次；已有摘要说明写入链路已经推进过，
     * 不能用更旧的通知覆盖，否则会把最新消息回退成旧内容。</p>
     */
    private void backfillOrder(long customerId) {
        var row = conversations.find(customerId, TYPE_ORDER);
        if (row == null || row.getLatestAt() != null) return;
        var latest = notifications.latest(customerId);
        if (latest == null) return;
        conversations.advance(customerId, TYPE_ORDER, TITLE_ORDER, latest.getContent(), latest.getCreatedAt());
    }

    /**
     * 会话类型到对外稳定标识的映射，前端据此决定点击行为。
     *
     * <p>订单中心使用固定值 system-order，其它类型按类型小写生成，保证同一会话每次返回相同标识。</p>
     */
    private String conversationId(String conversationType) {
        return TYPE_ORDER.equals(conversationType)
                ? CONVERSATION_ID_ORDER
                : "system-" + conversationType.toLowerCase();
    }
}
