package com.hbue.ordering.notification.config;

import com.hbue.ordering.notification.websocket.NotificationHandshakeInterceptor;
import com.hbue.ordering.notification.websocket.NotificationSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 共享WebSocket端点注册。限定允许来源并绑定握手认证，不为每个订单建立独立连接端点。
 */
@Configuration
@EnableWebSocket
public class NotificationWebSocketConfig implements WebSocketConfigurer {
    private final NotificationSocketHandler handler;
    private final NotificationHandshakeInterceptor interceptor;
    private final String[] origins;
    /** 注入当前配置所需组件；初始化只建立依赖关系，实际业务操作由请求入口触发。 */
    public NotificationWebSocketConfig(NotificationSocketHandler handler, NotificationHandshakeInterceptor interceptor,
            @Value("${notification.websocket.allowed-origins:http://localhost:8088,http://127.0.0.1:8088,http://localhost:5173,http://127.0.0.1:5173}") String origins) {
        this.handler = handler; this.interceptor = interceptor; this.origins = origins.split(",");
    }
    /**
     * 注册现有共享连接端点、握手拦截器及来源白名单，不为每笔订单开放独立端口。
     */
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/notifications/ws").addInterceptors(interceptor).setAllowedOrigins(origins);
    }
}
