package com.hbue.ordering.notification.dto;

/**
 * 评价评论站内信内部请求体。
 *
 * <p>由评论服务在评论事务提交后调用。{@code eventId} 形如
 * {@code review-discussion:{评论标识}:v{版本}}，与接收人共同构成通知表唯一约束，
 * 同一版本重试只落一条；编辑不重新通知。</p>
 *
 * @param recipientId  接收人内部用户编号
 * @param eventId      幂等事件标识
 * @param title        通知标题
 * @param content      通知正文
 * @param reviewType   评价类型：COMMENT 或 WINDOW_REVIEW，写入 biz_type
 * @param reviewId     评价公开标识，写入 biz_id 供消息中心跳转
 * @param discussionId 评论公开标识，供前端定位评论区
 * @author order-system
 * @date 2026-11-25
 */
public record ReviewDiscussionNotificationRequest(
        Long recipientId,
        String eventId,
        String title,
        String content,
        String reviewType,
        String reviewId,
        String discussionId
) {
}
