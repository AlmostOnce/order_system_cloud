package com.hbue.ordering.comment.client.vo;

/**
 * 评价评论站内信内部请求体。
 *
 * <p>由评论服务在评论事务提交后调用。{@code eventId} 使用"评论标识 + 版本"构成，
 * 与接收人共同保证通知表唯一约束，网络重试不会产生重复站内信；
 * 编辑不重新通知，撤回与屏蔽不产生通知。</p>
 *
 * @param recipientId  接收人内部用户编号
 * @param eventId      幂等事件标识，形如 review-discussion:{id}:v1
 * @param title        通知标题
 * @param content      通知正文，含评论人展示名
 * @param reviewType   评价类型：COMMENT 或 WINDOW_REVIEW
 * @param reviewId     评价公开标识，写入 biz_id 供消息中心跳转
 * @param discussionId 评论公开标识
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
