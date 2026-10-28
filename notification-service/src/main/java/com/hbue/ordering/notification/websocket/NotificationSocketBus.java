package com.hbue.ordering.notification.websocket;

import java.nio.charset.StandardCharsets;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Redis 只传播在线提示；漏提示时由持久化列表补齐。 */
@Component
public class NotificationSocketBus implements MessageListener {
    public static final String CHANNEL = "notification:socket:changed:v1";
    private final StringRedisTemplate redis;
    private final NotificationSocketHandler sockets;
    public NotificationSocketBus(StringRedisTemplate redis, NotificationSocketHandler sockets) { this.redis = redis; this.sockets = sockets; }
    public void publish(long userId) { redis.convertAndSend(CHANNEL, Long.toString(userId)); }
    @Override public void onMessage(Message message, byte[] pattern) {
        try {
            long userId = Long.parseLong(new String(message.getBody(), StandardCharsets.UTF_8));
            if (userId > 0) sockets.notifyUser(userId);
        } catch (NumberFormatException ignored) { }
    }
}
