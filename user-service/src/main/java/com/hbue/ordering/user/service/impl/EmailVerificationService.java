package com.hbue.ordering.user.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.redis.RedisOperationsService;
import com.hbue.ordering.user.dto.request.SendEmailCodeRequest;
import com.hbue.ordering.user.dto.request.VerifyEmailCaptchaRequest;
import com.hbue.ordering.user.service.EmailChangeAuthorization;
import com.hbue.ordering.user.service.EmailCodeSender;
import com.hbue.ordering.user.service.EmailVerification;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/** 邮箱验证码：与用户、目标邮箱绑定，限制发送与校验次数。 */
@Service
public class EmailVerificationService implements EmailVerification {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DefaultRedisScript<Long> CONSUME = new DefaultRedisScript<>("""
        local current = redis.call('GET', KEYS[1])
        if not current then return 0 end
        local digest, attempts = string.match(current, '^([a-f0-9]+):(%d+)$')
        if not digest then redis.call('DEL', KEYS[1]); return 0 end
        if digest == ARGV[1] then redis.call('DEL', KEYS[1]); return 1 end
        attempts = tonumber(attempts) + 1
        if attempts >= tonumber(ARGV[2]) then redis.call('DEL', KEYS[1])
        else redis.call('SET', KEYS[1], digest .. ':' .. attempts, 'KEEPTTL') end
        return 0
        """, Long.class);
    private static final DefaultRedisScript<Long> REMOVE_IF_SAME = new DefaultRedisScript<>("""
        local current = redis.call('GET', KEYS[1])
        if current and string.sub(current, 1, 64) == ARGV[1] then return redis.call('DEL', KEYS[1]) end
        return 0
        """, Long.class);
    private static final DefaultRedisScript<Long> CONSUME_MATCHING_TICKET = new DefaultRedisScript<>("""
        local current = redis.call('GET', KEYS[1])
        if current and current == ARGV[1] then return redis.call('DEL', KEYS[1]) end
        return 0
        """, Long.class);
    private final RedisOperationsService redis;
    private final EmailCodeSender sender;
    private final EmailChangeAuthorization authorization;
    private final String secret;

    public EmailVerificationService(RedisOperationsService redis, EmailCodeSender sender,
                                    EmailChangeAuthorization authorization,
                                    @Value("${email.verification.secret:}") String secret) {
        this.redis = redis;
        this.sender = sender;
        this.authorization = authorization;
        this.secret = secret;
    }

    @Override
    public String authorize(Long userId, String loginPassword) {
        return authorization.authorize(userId, loginPassword);
    }

    @Override
    public String verifyCaptcha(Long userId, VerifyEmailCaptchaRequest request) {
        if (request == null || secret.length() < 32) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "邮箱验证服务尚未配置");
        }
        authorization.verify(userId, request.authorizationToken());
        String email = normalize(request.email());
        String captchaId = request.captchaId();
        String answer = request.captchaCode();
        if (captchaId == null || !captchaId.matches("[A-Za-z0-9-]{1,64}") || answer == null || answer.isBlank()) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "图片验证码无效");
        }
        String expected = redis.getAndDelete("auth:captcha:" + captchaId);
        if (expected == null || !expected.equalsIgnoreCase(answer.strip())) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "图片验证码错误或已过期");
        }
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String ticket = HexFormat.of().formatHex(random);
        redis.setValue(captchaTicketKey(userId), captchaTicketDigest(userId, email,
                request.authorizationToken(), ticket), Duration.ofMinutes(5));
        return ticket;
    }

    @Override
    public void send(Long userId, SendEmailCodeRequest request) {
        if (userId == null || userId < 1 || request == null || secret.length() < 32) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "邮箱验证服务尚未配置");
        }
        authorization.verify(userId, request.authorizationToken());
        String email = normalize(request.email());
        if (request.captchaVerificationToken() == null
                || !request.captchaVerificationToken().matches("[a-f0-9]{64}")) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "请先完成人机验证");
        }
        String ticketDigest = captchaTicketDigest(userId, email, request.authorizationToken(), request.captchaVerificationToken());
        Long ticketConsumed = redis.executeScript(CONSUME_MATCHING_TICKET,
                List.of(captchaTicketKey(userId)), ticketDigest);
        if (!Long.valueOf(1).equals(ticketConsumed)) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "图片验证码验证已过期，请重新验证");
        }
        Long userCount = redis.increment("email:send:user:" + userId, Duration.ofSeconds(60));
        if (userCount == null || userCount > 1) {
            throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS, "发送过于频繁，请稍后再试");
        }
        Long emailCount = redis.increment("email:send:target:" + sha256(email), Duration.ofMinutes(10));
        if (emailCount == null || emailCount > 3) {
            throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS, "发送过于频繁，请稍后再试");
        }
        String code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        String key = key(userId);
        String value = digest(userId, email, code) + ":0";
        redis.setValue(key, value, Duration.ofMinutes(10));
        try {
            sender.send(email, code);
        } catch (RuntimeException exception) {
            // 比较后删除，不能误删并发请求产生的新验证码。
            redis.executeScript(REMOVE_IF_SAME, List.of(key), value.substring(0, 64));
            throw exception;
        }
    }

    @Override
    public void consume(Long userId, String authorizationToken, String email, String code) {
        if (userId == null || userId < 1 || code == null || !code.matches("[0-9]{6}") || secret.length() < 32) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "请输入有效的六位邮箱验证码");
        }
        authorization.verify(userId, authorizationToken);
        Long result = redis.executeScript(CONSUME, List.of(key(userId)), digest(userId, normalize(email), code), "5");
        if (!Long.valueOf(1).equals(result)) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "邮箱验证码错误、已过期或已使用");
        }
    }

    @Override
    public void verifyAuthorization(Long userId, String authorizationToken) {
        authorization.verify(userId, authorizationToken);
    }

    @Override
    public void consumeAuthorization(Long userId, String authorizationToken) {
        authorization.consume(userId, authorizationToken);
    }

    private String normalize(String email) {
        String value = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (value.length() > 254 || !value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "请输入正确的邮箱地址");
        }
        return value;
    }

    private String key(Long userId) { return "email:verification:user:" + userId; }
    private String captchaTicketKey(Long userId) { return "email:captcha:verified:" + userId; }

    private String digest(Long userId, String email, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((userId + "\n" + email + "\n" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("邮箱验证码计算失败", exception);
        }
    }

    private String captchaTicketDigest(Long userId, String email, String authorizationToken, String ticket) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String content = "captcha\n" + userId + "\n" + email + "\n" + authorizationToken + "\n" + ticket;
            return HexFormat.of().formatHex(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("图片验证凭据计算失败", exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("邮箱摘要计算失败", exception);
        }
    }
}
