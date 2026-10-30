package com.hbue.ordering.notification.config;

import com.hbue.ordering.notification.websocket.NotificationHandshakeInterceptor;
import com.hbue.ordering.notification.websocket.NotificationSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class NotificationWebSocketConfig implements WebSocketConfigurer {
    private final NotificationSocketHandler handler;
    private final NotificationHandshakeInterceptor interceptor;
    private final String[] origins;
    public NotificationWebSocketConfig(NotificationSocketHandler handler, NotificationHandshakeInterceptor interceptor,
            @Value("${notification.websocket.allowed-origins:http://localhost:8088,http://127.0.0.1:8088,http://localhost:5173,http://127.0.0.1:5173}") String origins) {
        this.handler = handler; this.interceptor = interceptor; this.origins = origins.split(",");
    }
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/notifications/ws").addInterceptors(interceptor).setAllowedOrigins(origins);
    }
}
