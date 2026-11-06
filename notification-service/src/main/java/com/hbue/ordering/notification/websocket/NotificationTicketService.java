package com.hbue.ordering.notification.websocket;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/**
 * 短时一次性连接票据服务。长期访问Token不进入WebSocket URL或Redis票据值。
 * 票据30秒过期，原子getAndDelete防重放；身份摘要用于连接建立后的注销与凭据版本校验。
 */
@Service
public class NotificationTicketService {
    private static final String PREFIX = "notification:ws-ticket:";
    private final SecureRandom random = new SecureRandom();
    private final StringRedisTemplate redis;
    /** 注入Redis票据存储；票据签发设置30秒TTL，消费必须用原子读取删除。 */
    public NotificationTicketService(StringRedisTemplate redis) { this.redis = redis; }
    /**
     * 生成随机一次性票据并保存30秒身份摘要。票据不能替代聊天资源授权，也不保存原始访问Token。
     */
    public String issue(Jwt jwt) {
        if (jwt.getId() == null || jwt.getExpiresAt() == null) throw new IllegalArgumentException("令牌信息不完整");
        long userId = Long.parseLong(jwt.getSubject());
        if (userId <= 0 || jwt.getId().contains("|")) throw new IllegalArgumentException("令牌信息不正确");
        String uuid=jwt.getClaimAsString("user_uuid");
        if(uuid==null||!com.hbue.ordering.common.web.security.JwtUserIdentity.PUBLIC_UUID.matcher(uuid).matches())
            throw new IllegalArgumentException("用户身份不完整");
        long credentialVersion=com.hbue.ordering.common.web.security.JwtUserIdentity.credentialVersion(jwt);
        // 使用安全随机数生成票据；登录Token本身不会被带入握手。
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(PREFIX + ticket, userId + "|" + jwt.getId() + "|" + jwt.getExpiresAt().getEpochSecond()+"|"+uuid+"|"+credentialVersion, Duration.ofSeconds(30));
        return ticket;
    }
    /**
     * 原子消费票据并检查身份摘要。格式错误、已过期、已消费或解析失败都返回空，禁止票据重放。
     */
    public SessionIdentity consume(String ticket) {
        if (ticket == null || !ticket.matches("[A-Za-z0-9_-]{43}")) return null;
        // 读取和删除必须是原子动作，防止多个连接同时重放同一票据。
        String value = redis.opsForValue().getAndDelete(PREFIX + ticket);
        if (value == null) return null;
        String[] fields = value.split("\\|", -1);
        if (fields.length != 5) return null;
        try {
            long userId = Long.parseLong(fields[0]); long expires = Long.parseLong(fields[2]);
            if (userId <= 0 || fields[1].isBlank() || expires <= java.time.Instant.now().getEpochSecond()) return null;
            return new SessionIdentity(userId, fields[1], expires, fields[3], Long.parseLong(fields[4]));
        } catch (NumberFormatException ignored) { return null; }
    }
    /**
     * 已消费票据形成的连接身份快照；不保存原始访问令牌或用户密码。
     * @param userId 已认证用户内部编号，供本人数据范围判断
     * @param tokenId 原访问令牌jti，供注销黑名单校验
     * @param expiresAt 原访问令牌到期秒时间戳，不能因Socket持续在线而续期
     * @param userUuid 用户公开UUID，供当前身份与凭据版本回源验证
     * @param credentialVersion 签发时凭据版本，改密后旧版本连接必须失效
     */
    public record SessionIdentity(long userId, String tokenId, long expiresAt, String userUuid, long credentialVersion) {}
}
