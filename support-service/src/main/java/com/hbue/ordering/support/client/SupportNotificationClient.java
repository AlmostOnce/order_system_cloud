package com.hbue.ordering.support.client;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.support.client.vo.SupportReplyNotificationRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 通知服务内部调用客户端。
 *
 * <p>商家公开回复落库成功后，再调用通知服务写站内信并通过 WebSocket 推送。
 * 通知失败不回滚回复，工单详情仍可看到回复；失败会在日志中记录并等待
 * 后续补偿任务按未通知回复重放。</p>
 *
 * @author order-system
 * @date 2026-11-23
 */
@FeignClient(name = "notification-service", contextId = "supportNotificationClient")
public interface SupportNotificationClient {

    /**
     * 写入并推送支持回复站内信。
     *
     * @param internalToken 服务间共享密钥
     * @param request       幂等事件与正文
     * @return 通知服务处理结果
     */
    @PostMapping("/internal/notifications/support-reply")
    ApiResponse<Void> sendSupportReply(
            @RequestHeader("X-Internal-Token") String internalToken,
            @RequestBody SupportReplyNotificationRequest request
    );
}
