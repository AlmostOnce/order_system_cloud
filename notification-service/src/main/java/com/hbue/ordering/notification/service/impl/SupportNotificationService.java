package com.hbue.ordering.notification.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.notification.dto.SupportReplyNotificationRequest;
import com.hbue.ordering.notification.mapper.NotificationMapper;
import com.hbue.ordering.notification.websocket.NotificationSocketBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * 支持回复站内信落库服务。
 *
 * <p>通知内容先持久化，再在事务提交后发布在线提示。接收人与来源事件标识的
 * 唯一约束吸收反馈服务的网络重试；重复事件只确认已存在，不重复推送。
 * 实时推送失败不回滚通知，学生可通过通知列表补齐。</p>
 *
 * @author order-system
 * @date 2026-11-23
 */
@Service
public class SupportNotificationService {

    /**
     * 当前类日志对象。
     */
    private static final Logger log = LoggerFactory.getLogger(SupportNotificationService.class);

    /**
     * 支持回复通知类型编码，前端可按此类型展示反馈图标。
     */
    private static final String SUPPORT_REPLY_TYPE = "SUPPORT_REPLY";

    /**
     * 来源事件标识最大长度，与通知表 event_id 列一致。
     */
    private static final int MAX_EVENT_ID_LENGTH = 128;

    /**
     * 通知标题最大长度，与通知表 title 列一致。
     */
    private static final int MAX_TITLE_LENGTH = 120;

    /**
     * 通知正文最大长度，与通知表 content 列一致。
     */
    private static final int MAX_CONTENT_LENGTH = 500;

    /**
     * 通知数据访问。
     */
    private final NotificationMapper repository;

    /**
     * 多实例在线提示总线。
     */
    private final NotificationSocketBus socketBus;

    /**
     * 创建支持回复通知服务。
     *
     * @param repository 通知数据访问
     * @param socketBus  多实例在线提示总线
     */
    public SupportNotificationService(NotificationMapper repository, NotificationSocketBus socketBus) {
        this.repository = repository;
        this.socketBus = socketBus;
    }

    /**
     * 幂等写入支持回复站内信。
     *
     * <p>参数来自可信内部调用，仍做长度与空值防御；标题与正文超长时截断，
     * 事件标识缺失或接收人非法直接拒绝，避免产生无法追溯的通知记录。</p>
     *
     * @param request 内部请求
     */
    @Transactional
    public void sendSupportReply(SupportReplyNotificationRequest request) {
        if (request == null || request.recipientId() == null || request.recipientId() <= 0) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "通知接收人无效");
        }
        if (request.eventId() == null || request.eventId().isBlank()
                || request.eventId().length() > MAX_EVENT_ID_LENGTH) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "通知事件标识无效");
        }
        String title = truncate(request.title(), MAX_TITLE_LENGTH);
        String content = truncate(request.content(), MAX_CONTENT_LENGTH);
        long recipientId = request.recipientId();
        try {
            repository.insertEvent(UUID.randomUUID().toString(), recipientId, request.eventId(),
                    SUPPORT_REPLY_TYPE, title, content);
            // 只有通知记录真正提交后才提示在线客户端刷新，回滚时不发送虚假变化。
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        socketBus.publish(recipientId);
                    } catch (RuntimeException exception) {
                        // 在线提示失败不影响通知事实，客户端下次拉取列表即可补齐。
                        log.warn("支持回复实时提示发送失败，recipientId={}", recipientId);
                    }
                }
            });
        } catch (DuplicateKeyException exception) {
            // 只有确认是同一接收人的同一来源事件才视为幂等成功，其他唯一键冲突继续抛出。
            if (!repository.existsEvent(recipientId, request.eventId())) {
                throw exception;
            }
        }
    }

    /**
     * 截断通知文本。
     *
     * @param value     原始文本
     * @param maxLength 最大长度
     * @return 非空且不超长的文本
     */
    private String truncate(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            return "您有一条新的反馈回复";
        }
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
