package com.hbue.ordering.notification.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.notification.websocket.NotificationTicketService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 已登录用户申请一次性WebSocket票据的HTTP入口。返回票据及30秒有效时长，不返回长期Token。
 */
@RestController
@RequestMapping("/notifications/socket-ticket")
public class NotificationSocketTicketController {
    public record Ticket(String ticket, int expiresInSeconds) {}
    private final NotificationTicketService tickets;
    /** 注入一次性票据服务；HTTP入口先经资源服务器认证，再签发短效握手凭据。 */
    public NotificationSocketTicketController(NotificationTicketService tickets) { this.tickets = tickets; }
    /**
     * 根据认证JWT生成一次性连接票据并返回有效期。此方法不创建订单会话，也不向响应中复制访问Token。
     */
    @PostMapping
    public ApiResponse<Ticket> create(@AuthenticationPrincipal Jwt jwt) { return ApiResponse.success(new Ticket(tickets.issue(jwt), 30)); }
}
