package com.hbue.ordering.notification.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.notification.service.NotificationService;
import com.hbue.ordering.notification.vo.NotificationPageVO;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * 本人系统通知HTTP接口。查询和标记已读均使用JWT解析后的用户编号，不接受接收人参数。
 */
@RestController
@RequestMapping("/notifications")
public class NotificationController {
    private final NotificationService service;
    /** 注入本人通知业务接口；认证后的内部用户编号由JWT解析结果提供。 */
    public NotificationController(NotificationService service) { this.service = service; }
    /**
     * 按当前用户可访问范围查询游标页。游标属于其他用户时拒绝；额外取一条判断下一页，避免加载全部记录。
     */
    @GetMapping
    public ApiResponse<NotificationPageVO> page(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(name="cursor", required=false) String cursor, @RequestParam(name="limit", defaultValue="20") int limit) {
        return ApiResponse.success(service.page(Long.parseLong(jwt.getSubject()), cursor, limit));
    }
    /**
     * 返回当前认证用户尚未阅读的系统订单通知数量，与商家聊天未读保持独立。
     */
    @GetMapping("/unread-count")
    public ApiResponse<Long> unread(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.unreadCount(Long.parseLong(jwt.getSubject())));
    }
    /**
     * 确认通知属于本人后标记已读；不存在与他人通知统一拒绝，不让客户端指定接收人。
     */
    @PostMapping("/{notificationId}/read")
    public ApiResponse<Void> markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable("notificationId") String id) {
        service.markRead(Long.parseLong(jwt.getSubject()), id);
        return ApiResponse.success(null);
    }
    /**
     * 只更新当前认证用户的全部系统通知，不改变商家聊天的独立阅读水位。
     */
    @PostMapping("/read-all")
    public ApiResponse<Void> markAllRead(@AuthenticationPrincipal Jwt jwt) {
        service.markAllRead(Long.parseLong(jwt.getSubject()));
        return ApiResponse.success(null);
    }
}
