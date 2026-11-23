package com.hbue.ordering.notification.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.common.web.security.InternalScopeTokenVerifier;
import com.hbue.ordering.notification.dto.SupportReplyNotificationRequest;
import com.hbue.ordering.notification.dto.SupportStatusNotificationRequest;
import com.hbue.ordering.notification.service.impl.SupportNotificationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知服务内部接口。
 *
 * <p>仅允许服务之间通过共享密钥调用，网关不配置 {@code /internal/**} 路由。
 * 安全过滤链对该前缀匿名放行，由本控制器逐个校验 {@code X-Internal-Token}，
 * 校验失败统一返回 401。</p>
 *
 * @author order-system
 * @date 2026-11-23
 */
@RestController
@RequestMapping("/internal/notifications")
public class NotificationInternalController {

    /**
     * 支持回复通知业务服务。
     */
    private final SupportNotificationService supportNotificationService;

    /**
     * 服务间共享密钥校验器。
     */
    private final InternalScopeTokenVerifier internalScopeTokenVerifier;

    /**
     * 创建通知内部接口控制器。
     *
     * @param supportNotificationService 支持回复通知业务服务
     * @param internalScopeTokenVerifier 服务间共享密钥校验器
     */
    public NotificationInternalController(SupportNotificationService supportNotificationService,
                                          InternalScopeTokenVerifier internalScopeTokenVerifier) {
        this.supportNotificationService = supportNotificationService;
        this.internalScopeTokenVerifier = internalScopeTokenVerifier;
    }

    /**
     * 写入并推送支持回复站内信。
     *
     * @param internalToken 服务间共享密钥
     * @param request       幂等事件与正文
     * @return 处理成功响应
     */
    @PostMapping("/support-reply")
    public ApiResponse<Void> supportReply(
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
            @RequestBody SupportReplyNotificationRequest request) {
        internalScopeTokenVerifier.verify(internalToken);
        supportNotificationService.sendSupportReply(request);
        return ApiResponse.success(null);
    }

    /**
     * 写入并推送工单状态变更站内信。
     *
     * <p>工单受理或关闭事务提交后由反馈服务调用，与回复通知共用内部鉴权和幂等落库，
     * 区别只在通知类型与业务文案，学生端消息中心无需区分接口来源。</p>
     *
     * @param internalToken 服务间共享密钥
     * @param request       幂等事件与正文
     * @return 处理成功响应
     */
    @PostMapping("/support-status")
    public ApiResponse<Void> supportStatus(
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
            @RequestBody SupportStatusNotificationRequest request) {
        internalScopeTokenVerifier.verify(internalToken);
        supportNotificationService.sendSupportStatus(request);
        return ApiResponse.success(null);
    }
}
