package com.hbue.ordering.notification.websocket;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class NotificationSocketHandler extends TextWebSocketHandler implements SubProtocolCapable {
    public static final String IDENTITY = "notificationIdentity";
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final StringRedisTemplate redis;
    private final String blacklistPrefix;
    public NotificationSocketHandler(StringRedisTemplate redis, @Value("${security.jwt.blacklist-key-prefix:auth:access-token:blacklist:}") String prefix) {
        this.redis = redis; this.blacklistPrefix = prefix;
    }
    @Override public List<String> getSubProtocols() { return List.of("notification.v1"); }
    @Override public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Object identity = session.getAttributes().get(IDENTITY);
        if (!(identity instanceof NotificationTicketService.SessionIdentity) || !valid((NotificationTicketService.SessionIdentity) identity)) {
            session.close(CloseStatus.POLICY_VIOLATION); return;
        }
        sessions.put(session.getId(), new ConcurrentWebSocketSessionDecorator(session, 3000, 16384));
    }
    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) { sessions.remove(session.getId()); }
    @Override public void handleTransportError(WebSocketSession session, Throwable error) throws Exception { sessions.remove(session.getId()); if (session.isOpen()) session.close(CloseStatus.SERVER_ERROR); }
    public void notifyUser(long userId) {
        for (WebSocketSession session : sessions.values()) {
            Object value = session.getAttributes().get(IDENTITY);
            if (!(value instanceof NotificationTicketService.SessionIdentity identity) || identity.userId() != userId) continue;
            if (!valid(identity)) { close(session); continue; }
            try { session.sendMessage(new TextMessage("{\"type\":\"notification.changed\"}")); }
            catch (IOException | RuntimeException error) { close(session); }
        }
    }
    @Scheduled(fixedDelay = 30000)
    public void closeExpiredSessions() {
        for (WebSocketSession session : sessions.values()) {
            Object value = session.getAttributes().get(IDENTITY);
            if (!(value instanceof NotificationTicketService.SessionIdentity identity) || !valid(identity)) close(session);
        }
    }
    private boolean valid(NotificationTicketService.SessionIdentity identity) {
        if (identity.expiresAt() <= Instant.now().getEpochSecond()) return false;
        try { return Boolean.FALSE.equals(redis.hasKey(blacklistPrefix + identity.tokenId())); }
        catch (RuntimeException error) { return false; }
    }
    private void close(WebSocketSession session) {
        sessions.remove(session.getId());
        try { if (session.isOpen()) session.close(CloseStatus.POLICY_VIOLATION); }
        catch (IOException ignored) { }
    }
}
