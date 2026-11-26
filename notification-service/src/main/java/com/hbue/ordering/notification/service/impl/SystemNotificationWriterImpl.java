package com.hbue.ordering.notification.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.notification.dto.NotificationDraft;
import com.hbue.ordering.notification.mapper.NotificationMapper;
import com.hbue.ordering.notification.service.NotificationPreferenceService;
import com.hbue.ordering.notification.service.SystemNotificationWriter;
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
 * 站内信统一写入实现。
 *
 * <p>写入顺序：先校验接收人与事件标识，再截断标题/正文，最后落库。任何一步失败都不写库，
 * 避免产生"无法追溯来源"或"内容空白"的通知。</p>
 *
 * <p>幂等由数据库唯一键 {@code (recipient_id, event_id)} 保证：重复键只有在该接收人确实
 * 已存在同一来源事件时才视为成功，其它唯一键冲突继续抛出，不能把真实故障当成重试吸收掉。</p>
 *
 * <p>偏好只影响提交后的实时提示与未读角标，通知记录一律落库；这样学生关闭提醒后
 * 仍能在消息中心看到历史内容，重新打开也不会丢失。</p>
 *
 * @author order-system
 * @date 2026-11-25
 */
@Service
public class SystemNotificationWriterImpl implements SystemNotificationWriter {

    /**
     * 当前类日志对象。
     */
    private static final Logger log = LoggerFactory.getLogger(SystemNotificationWriterImpl.class);

    /**
     * 来源事件标识最大长度，与通知表 event_id 列一致。
     */
    private static final int MAX_EVENT_ID_LENGTH = 128;

    /**
     * 业务公开标识最大长度，与通知表 biz_id 列一致。
     */
    private static final int MAX_BIZ_ID_LENGTH = 64;

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
     * 通知偏好业务，用于判断是否推送实时提示。
     */
    private final NotificationPreferenceService preferences;

    /**
     * 创建站内信写入器。
     *
     * @param repository  通知数据访问
     * @param socketBus   多实例在线提示总线
     * @param preferences 通知偏好业务
     */
    public SystemNotificationWriterImpl(NotificationMapper repository, NotificationSocketBus socketBus,
                                        NotificationPreferenceService preferences) {
        this.repository = repository;
        this.socketBus = socketBus;
        this.preferences = preferences;
    }

    /**
     * 写入一条站内信并在事务提交后推送在线提示。
     *
     * @param draft 站内信草稿
     */
    @Override
    @Transactional
    public void write(NotificationDraft draft) {
        if (draft == null) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "通知请求不能为空");
        }
        Long recipientId = draft.recipientId();
        if (recipientId == null || recipientId <= 0) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "通知接收人无效");
        }
        String eventId = draft.eventId();
        if (eventId == null || eventId.isBlank() || eventId.length() > MAX_EVENT_ID_LENGTH) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "通知事件标识无效");
        }
        String safeTitle = truncate(draft.title(), MAX_TITLE_LENGTH, draft.fallbackTitle());
        String safeContent = truncate(draft.content(), MAX_CONTENT_LENGTH, draft.fallbackContent());
        // 业务标识只用于消息中心跳转，为空时保持不可跳转，不影响通知本身展示。
        String bizId = draft.bizId() == null || draft.bizId().isBlank() ? null : draft.bizId().trim();
        if (bizId != null && bizId.length() > MAX_BIZ_ID_LENGTH) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "通知业务标识无效");
        }
        try {
            repository.insertEvent(UUID.randomUUID().toString(), recipientId, eventId,
                    draft.type(), safeTitle, safeContent, draft.bizType(), bizId);
            // 通知记录始终落库；关闭该类提醒时只跳过实时提示，消息中心仍可查看。
            if (preferences.isEnabled(recipientId, draft.category())) {
                // 只有通知记录真正提交后才提示在线客户端刷新，回滚时不发送虚假变化。
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            socketBus.publish(recipientId);
                        } catch (RuntimeException exception) {
                            // 在线提示失败不影响通知事实，客户端下次拉取列表即可补齐。
                            log.warn("站内信实时提示发送失败，recipientId={}", recipientId);
                        }
                    }
                });
            }
        } catch (DuplicateKeyException exception) {
            // 只有确认是同一接收人的同一来源事件才视为幂等成功，其他唯一键冲突继续抛出。
            if (!repository.existsEvent(recipientId, eventId)) {
                throw exception;
            }
        }
    }

    /**
     * 截断通知文本。
     *
     * @param value        原始文本
     * @param maxLength    最大长度
     * @param defaultValue 文本为空时的兜底文案
     * @return 非空且不超长的文本
     */
    private String truncate(String value, int maxLength, String defaultValue) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            return defaultValue;
        }
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
