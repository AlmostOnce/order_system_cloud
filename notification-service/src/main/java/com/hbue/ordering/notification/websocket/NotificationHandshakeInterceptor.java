package com.hbue.ordering.notification.websocket;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * WebSocket握手认证。只接受notification.v1与单个ticket子协议，消费一次性票据后绑定服务端身份。
 * 握手不信任URL中的用户编号；票据存储故障同样拒绝建立连接。
 */
@Component
public class NotificationHandshakeInterceptor implements HandshakeInterceptor {
    private final NotificationTicketService tickets;
    /** 注入票据消费服务；握手使用一次性票据绑定身份，不从URL接收长期访问Token。 */
    public NotificationHandshakeInterceptor(NotificationTicketService tickets) { this.tickets = tickets; }
    /**
     * 解析协议与唯一票据，原子消费后保存服务端身份。缺少协议、重复票据、过期和存储异常均拒绝升级。
     */
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
    /**
     * 握手结束回调。业务身份已在握手前绑定，此阶段不补建身份或绕过失败的认证。
     */
    @Override public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler, Exception error) { }
}
