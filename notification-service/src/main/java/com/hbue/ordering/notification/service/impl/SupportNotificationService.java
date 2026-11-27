package com.hbue.ordering.notification.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.notification.dto.NotificationDraft;
import com.hbue.ordering.notification.dto.SupportReplyNotificationRequest;
import com.hbue.ordering.notification.dto.SupportStatusNotificationRequest;
import com.hbue.ordering.notification.model.NotificationCategory;
import com.hbue.ordering.notification.service.SystemNotificationWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 支持回复站内信落库服务。
 *
 * <p>通知内容先持久化，再由统一写入器在事务提交后发布在线提示。接收人与来源事件标识的
 * 唯一约束吸收反馈服务的网络重试；重复事件只确认已存在，不重复推送。</p>
 *
 * <p>本类只负责把反馈业务的参数翻译成站内信草稿：类型编码、兜底文案与业务跳转类型
 * 都由这里定义，长度截断、偏好判断与实时推送交给 {@link SystemNotificationWriter}，
 * 与优惠券到期提醒等其它系统通知共用同一套约束。</p>
 *
 * @author order-system
 * @date 2026-11-23
 */
@Service
public class SupportNotificationService {

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
     * 站内信统一写入器。
     */
    private final SystemNotificationWriter writer;

    /**
     * 创建支持回复通知服务。
     *
     * @param writer 站内信统一写入器
     */
    public SupportNotificationService(SystemNotificationWriter writer) {
        this.writer = writer;
    }

    /**
     * 幂等写入支持回复站内信。
     *
     * <p>商家公开回复提交后调用。参数来自可信内部调用，仍由写入器做长度与空值防御；
     * 事件标识缺失或接收人非法直接拒绝，避免产生无法追溯的通知记录。</p>
     *
     * @param request 内部请求
     */
    @Transactional
    public void sendSupportReply(SupportReplyNotificationRequest request) {
        if (request == null) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "通知请求不能为空");
        }
        writer.write(new NotificationDraft(request.recipientId(), request.eventId(), SUPPORT_REPLY_TYPE,
                request.title(), request.content(), DEFAULT_TITLE, "您有一条新的反馈回复",
                SUPPORT_BIZ_TYPE, request.ticketId(), NotificationCategory.SUPPORT));
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
        writer.write(new NotificationDraft(request.recipientId(), request.eventId(), SUPPORT_STATUS_TYPE,
                request.title(), request.content(), DEFAULT_TITLE, "您提交的反馈状态已更新",
                SUPPORT_BIZ_TYPE, request.ticketId(), NotificationCategory.SUPPORT));
    }
}
