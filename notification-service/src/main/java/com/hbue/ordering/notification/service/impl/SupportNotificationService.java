package com.hbue.ordering.notification.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.notification.dto.SupportReplyNotificationRequest;
import com.hbue.ordering.notification.dto.SupportStatusNotificationRequest;
import com.hbue.ordering.notification.mapper.NotificationMapper;
import com.hbue.ordering.notification.model.NotificationCategory;
import com.hbue.ordering.notification.service.NotificationPreferenceService;
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
     * 支持工单状态变更通知类型编码，用于受理与关闭提醒。
     *
     * <p>与回复通知分开编码，客户端可以区分「有新回复」和「状态已变化」；
     * 长度不超过通知表 notification_type 列的 32 字符上限。</p>
     */
    private static final String SUPPORT_STATUS_TYPE = "SUPPORT_STATUS";

    /**
     * 通知标题兜底文案，标题为空时使用。
     */
    private static final String DEFAULT_TITLE = "反馈处理进展";

    /**
     * 反馈工单业务类型编码，写入 biz_type 供消息中心识别跳转目标。
     */
    private static final String SUPPORT_BIZ_TYPE = "SUPPORT";

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
     * 创建支持回复通知服务。
     *
     * @param repository  通知数据访问
     * @param socketBus   多实例在线提示总线
     * @param preferences 通知偏好业务
     */
    public SupportNotificationService(NotificationMapper repository, NotificationSocketBus socketBus,
                                      NotificationPreferenceService preferences) {
        this.repository = repository;
        this.socketBus = socketBus;
        this.preferences = preferences;
    }

    /**
     * 幂等写入支持回复站内信。
     *
     * <p>商家公开回复提交后调用。参数来自可信内部调用，仍做长度与空值防御；
     * 标题与正文超长时截断，事件标识缺失或接收人非法直接拒绝，
     * 避免产生无法追溯的通知记录。</p>
     *
     * @param request 内部请求
     */
    @Transactional
    public void sendSupportReply(SupportReplyNotificationRequest request) {
        if (request == null) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "通知请求不能为空");
        }
        send(request.recipientId(), request.eventId(), SUPPORT_REPLY_TYPE,
                request.title(), request.content(), "您有一条新的反馈回复", request.ticketId());
    }

    /**
     * 幂等写入工单状态变更站内信。
     *
     * <p>工单受理或关闭提交后调用，提醒学生状态已经变化。与回复通知共用落库与
     * 在线提示逻辑，仅通知类型和正文兜底文案不同；同一系统时间线消息的网络重试
     * 由接收人与事件标识的唯一约束吸收，不会重复提醒。</p>
     *
     * @param request 内部请求
     */
    @Transactional
    public void sendSupportStatus(SupportStatusNotificationRequest request) {
        if (request == null) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "通知请求不能为空");
        }
        send(request.recipientId(), request.eventId(), SUPPORT_STATUS_TYPE,
                request.title(), request.content(), "您提交的反馈状态已更新", request.ticketId());
    }

    /**
     * 写入站内信并在事务提交后推送在线提示。
     *
     * <p>接收人、事件标识与文本在写库前统一校验和截断；只有通知记录真正提交后
     * 才提示在线客户端刷新，回滚时不发送虚假变化。重复键仅在确认是同一接收人的
     * 同一来源事件时视为幂等成功，其他数据库冲突继续抛出交由调用方处理。</p>
     *
     * @param recipientId    接收学生内部用户编号
     * @param eventId        来源业务事件标识
     * @param type           通知类型编码
     * @param title          通知标题
     * @param content        通知正文
     * @param defaultContent 正文为空时的兜底文案
     * @param ticketId       公开工单标识，可空；非空时写入 biz_id 供消息中心跳转
     */
    private void send(Long recipientId, String eventId, String type,
                      String title, String content, String defaultContent, String ticketId) {
        if (recipientId == null || recipientId <= 0) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "通知接收人无效");
        }
        if (eventId == null || eventId.isBlank() || eventId.length() > MAX_EVENT_ID_LENGTH) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "通知事件标识无效");
        }
        String safeTitle = truncate(title, MAX_TITLE_LENGTH, DEFAULT_TITLE);
        String safeContent = truncate(content, MAX_CONTENT_LENGTH, defaultContent);
        // 公开工单标识长度受数据库列限制；为空时保持不可跳转，不影响通知本身展示。
        String bizId = ticketId == null || ticketId.isBlank() ? null : ticketId.trim();
        if (bizId != null && bizId.length() > MAX_BIZ_ID_LENGTH) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "反馈编号无效");
        }
        try {
            repository.insertEvent(UUID.randomUUID().toString(), recipientId, eventId,
                    type, safeTitle, safeContent, SUPPORT_BIZ_TYPE, bizId);
            // 通知记录始终落库；关闭反馈提醒时只跳过实时提示，消息中心仍可查看。
            if (preferences.isEnabled(recipientId, NotificationCategory.SUPPORT)) {
                // 只有通知记录真正提交后才提示在线客户端刷新，回滚时不发送虚假变化。
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            socketBus.publish(recipientId);
                        } catch (RuntimeException exception) {
                            // 在线提示失败不影响通知事实，客户端下次拉取列表即可补齐。
                            log.warn("反馈通知实时提示发送失败，recipientId={}", recipientId);
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
