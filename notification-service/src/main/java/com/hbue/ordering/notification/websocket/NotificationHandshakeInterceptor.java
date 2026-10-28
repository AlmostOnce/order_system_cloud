package com.hbue.ordering.notification.websocket;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Component
public class NotificationHandshakeInterceptor implements HandshakeInterceptor {
    private final NotificationTicketService tickets;
    public NotificationHandshakeInterceptor(NotificationTicketService tickets) { this.tickets = tickets; }
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler, Map<String,Object> attributes) {
        String protocols = request.getHeaders().getFirst("Sec-WebSocket-Protocol");
        String ticket = null;
        boolean validProtocol = false;
        if (protocols != null) for (String part : protocols.split(",")) {
            String value = part.trim();
            if ("notification.v1".equals(value)) validProtocol = true;
            if (value.startsWith("ticket.")) { if (ticket != null) return false; ticket = value.substring(7); }
        }
        try {
            var identity = validProtocol ? tickets.consume(ticket) : null;
            if (identity != null) { attributes.put(NotificationSocketHandler.IDENTITY, identity); return true; }
        } catch (RuntimeException ignored) { /* 票据存储故障时拒绝连接。 */ }
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
    }
    @Override public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler, Exception error) { }
}
