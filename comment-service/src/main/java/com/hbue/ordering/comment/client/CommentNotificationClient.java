package com.hbue.ordering.comment.client;

import com.hbue.ordering.comment.client.vo.ReviewDiscussionNotificationRequest;
import com.hbue.ordering.common.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 通知服务内部客户端。
 *
 * <p>评论或回复落库并提交后，再调用通知服务写站内信并通过 WebSocket 提示。
 * 通知属于最终一致环节：调用失败只记日志，不回滚已提交的评论，也不向用户报错，
 * 因为评论本身已经可以在评论区看到。</p>
 *
 * @author order-system
 * @date 2026-11-25
 */
@FeignClient(name = "notification-service", contextId = "commentNotificationClient")
public interface CommentNotificationClient {

    /**
     * 写入并推送评价评论站内信。
     *
     * @param internalToken 服务间共享令牌
     * @param request       幂等事件与正文
     * @return 通知服务处理结果
     */
    @PostMapping("/internal/notifications/review-discussion")
    ApiResponse<Void> sendReviewDiscussion(
            @RequestHeader("X-Internal-Token") String internalToken,
            @RequestBody ReviewDiscussionNotificationRequest request
    );
}
