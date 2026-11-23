package com.hbue.ordering.support.client;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.support.client.vo.SupportReplyNotificationRequest;
import com.hbue.ordering.support.client.vo.SupportStatusNotificationRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 通知服务内部调用客户端。
 *
 * <p>商家公开回复或工单状态变更落库成功后，再调用通知服务写站内信并通过
 * WebSocket 推送。通知失败不回滚已提交的业务操作，工单详情仍可看到最新状态；
 * 失败会在日志中记录并等待后续补偿任务按未通知事件重放。</p>
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

    /**
     * 写入并推送工单状态变更站内信。
     *
     * <p>受理或关闭事务提交后调用。事件编号取系统时间线消息标识，
     * 通知服务按接收人与事件编号幂等，重复调用不会产生多条提醒。</p>
     *
     * @param internalToken 服务间共享密钥
     * @param request       幂等事件与正文
     * @return 通知服务处理结果
     */
    @PostMapping("/internal/notifications/support-status")
    ApiResponse<Void> sendSupportStatus(
            @RequestHeader("X-Internal-Token") String internalToken,
            @RequestBody SupportStatusNotificationRequest request
    );
}
