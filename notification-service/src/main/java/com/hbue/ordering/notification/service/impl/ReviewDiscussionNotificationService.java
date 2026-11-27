package com.hbue.ordering.notification.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.notification.dto.NotificationDraft;
import com.hbue.ordering.notification.dto.ReviewDiscussionNotificationRequest;
import com.hbue.ordering.notification.model.NotificationCategory;
import com.hbue.ordering.notification.service.SystemNotificationWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 评价评论站内信的写入。
 *
 * <p>评论或回复落库后由评论服务调用。通知类型独立为 {@code REVIEW_DISCUSSION}，
 * 偏好分类独立为 {@code REVIEW}，学生可以单独关闭评价互动提醒，
 * 不影响订单状态与工单回复。</p>
 *
 * <p>业务类型限制为评价的两种编码，避免调用方把任意字符串写进 {@code biz_type}，
 * 导致消息中心拿到无法识别的跳转目标。</p>
 *
 * @author order-system
 * @date 2026-11-25
 */
@Service
public class ReviewDiscussionNotificationService {

    /**
     * 评价评论通知类型编码，长度不超过通知表的 32 字符上限。
     */
    private static final String REVIEW_DISCUSSION_TYPE = "REVIEW_DISCUSSION";

    /**
     * 菜品评价业务类型编码，写入 biz_type。
     */
    private static final String BIZ_TYPE_COMMENT = "COMMENT";

    /**
     * 店铺评价业务类型编码，写入 biz_type。
     */
    private static final String BIZ_TYPE_WINDOW_REVIEW = "WINDOW_REVIEW";

    /**
     * 标题兜底文案，标题为空时使用。
     */
    private static final String DEFAULT_TITLE = "评价互动提醒";

    /**
     * 正文兜底文案，正文为空时使用。
     */
    private static final String DEFAULT_CONTENT = "你的评价有了新的互动";

    /**
     * 站内信统一写入器。
     */
    private final SystemNotificationWriter writer;

    /**
     * 创建评价评论通知服务。
     *
     * @param writer 站内信统一写入器
     */
    public ReviewDiscussionNotificationService(SystemNotificationWriter writer) {
        this.writer = writer;
    }

    /**
     * 写入并推送评价评论站内信。
     *
     * <p>同一接收人同一事件标识重复调用按幂等成功处理，不会产生第二条通知；
     * 偏好关闭时只抑制实时提示与未读角标，通知记录仍保留。</p>
     *
     * @param request 内部请求
     */
    @Transactional
    public void send(ReviewDiscussionNotificationRequest request) {
        if (request == null) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "通知请求不能为空");
        }
        // biz_type 只接受评价的两种编码：未知类型说明调用方契约不一致，直接拒绝而不是原样落库。
        String bizType = request.reviewType();
        if (!BIZ_TYPE_COMMENT.equals(bizType) && !BIZ_TYPE_WINDOW_REVIEW.equals(bizType)) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "评价类型不正确");
        }
        writer.write(new NotificationDraft(request.recipientId(), request.eventId(), REVIEW_DISCUSSION_TYPE,
                request.title(), request.content(), DEFAULT_TITLE, DEFAULT_CONTENT,
                bizType, request.reviewId(), NotificationCategory.REVIEW));
    }
}
