package com.hbue.ordering.notification.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.notification.websocket.NotificationTicketService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications/socket-ticket")
public class NotificationSocketTicketController {
    public record Ticket(String ticket, int expiresInSeconds) {}
    private final NotificationTicketService tickets;
    public NotificationSocketTicketController(NotificationTicketService tickets) { this.tickets = tickets; }
    @PostMapping
    public ApiResponse<Ticket> create(@AuthenticationPrincipal Jwt jwt) { return ApiResponse.success(new Ticket(tickets.issue(jwt), 30)); }
}
