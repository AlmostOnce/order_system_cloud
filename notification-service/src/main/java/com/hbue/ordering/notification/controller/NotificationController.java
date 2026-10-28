package com.hbue.ordering.notification.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.notification.service.NotificationService;
import com.hbue.ordering.notification.vo.NotificationPageVO;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/notifications")
public class NotificationController {
    private final NotificationService service;
    public NotificationController(NotificationService service) { this.service = service; }
    @GetMapping
    public ApiResponse<NotificationPageVO> page(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(name="cursor", required=false) String cursor, @RequestParam(name="limit", defaultValue="20") int limit) {
        return ApiResponse.success(service.page(Long.parseLong(jwt.getSubject()), cursor, limit));
    }
    @GetMapping("/unread-count")
    public ApiResponse<Long> unread(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.unreadCount(Long.parseLong(jwt.getSubject())));
    }
    @PostMapping("/{notificationId}/read")
    public ApiResponse<Void> markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable("notificationId") String id) {
        service.markRead(Long.parseLong(jwt.getSubject()), id);
        return ApiResponse.success(null);
    }
    @PostMapping("/read-all")
    public ApiResponse<Void> markAllRead(@AuthenticationPrincipal Jwt jwt) {
        service.markAllRead(Long.parseLong(jwt.getSubject()));
        return ApiResponse.success(null);
    }
}
