package com.hbue.ordering.notification.websocket;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/** 一次性短期握手票据；不保存或传递访问令牌。 */
@Service
public class NotificationTicketService {
    private static final String PREFIX = "notification:ws-ticket:";
    private final SecureRandom random = new SecureRandom();
    private final StringRedisTemplate redis;
    public NotificationTicketService(StringRedisTemplate redis) { this.redis = redis; }
    public String issue(Jwt jwt) {
        if (jwt.getId() == null || jwt.getExpiresAt() == null) throw new IllegalArgumentException("令牌信息不完整");
        long userId = Long.parseLong(jwt.getSubject());
        if (userId <= 0 || jwt.getId().contains("|")) throw new IllegalArgumentException("令牌信息不正确");
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(PREFIX + ticket, userId + "|" + jwt.getId() + "|" + jwt.getExpiresAt().getEpochSecond(), Duration.ofSeconds(30));
        return ticket;
    }
    public SessionIdentity consume(String ticket) {
        if (ticket == null || !ticket.matches("[A-Za-z0-9_-]{43}")) return null;
        String value = redis.opsForValue().getAndDelete(PREFIX + ticket);
        if (value == null) return null;
        String[] fields = value.split("\\|", -1);
        if (fields.length != 3) return null;
        try {
            long userId = Long.parseLong(fields[0]); long expires = Long.parseLong(fields[2]);
            if (userId <= 0 || fields[1].isBlank() || expires <= java.time.Instant.now().getEpochSecond()) return null;
            return new SessionIdentity(userId, fields[1], expires);
        } catch (NumberFormatException ignored) { return null; }
    }
    public record SessionIdentity(long userId, String tokenId, long expiresAt) {}
}
