package com.hbue.ordering.user.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.redis.RedisOperationsService;
import com.hbue.ordering.user.mapper.UserMapper;
import com.hbue.ordering.user.model.UserDO;
import com.hbue.ordering.user.service.EmailChangeAuthorization;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** 登录密码校验后签发短时、绑定当前用户且仅可成功消费一次的邮箱变更凭据。 */
@Service
public class EmailChangeAuthorizationService implements EmailChangeAuthorization {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration TOKEN_TTL = Duration.ofMinutes(10);
    private static final String KEY_PREFIX = "email:change:authorization:";
    private static final DefaultRedisScript<Long> CONSUME_IF_MATCH = new DefaultRedisScript<>("""
        local current = redis.call('GET', KEYS[1])
        if current and current == ARGV[1] then return redis.call('DEL', KEYS[1]) end
        return 0
        """, Long.class);

    private final UserMapper users;
    private final PasswordEncoder passwords;
    private final RedisOperationsService redis;

    public EmailChangeAuthorizationService(UserMapper users, PasswordEncoder passwords,
                                           RedisOperationsService redis) {
        this.users = users;
        this.passwords = passwords;
        this.redis = redis;
    }

    @Override
    public String authorize(Long userId, String loginPassword) {
        if (userId == null || userId < 1 || loginPassword == null || loginPassword.isBlank()
                || loginPassword.length() > 64) {
            throw invalidPassword();
        }
        UserDO user = users.selectPasswordsById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus()) || user.getDeletedAt() != null
                || user.getPasswordHash() == null || !passwords.matches(loginPassword, user.getPasswordHash())) {
            throw invalidPassword();
        }
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String token = HexFormat.of().formatHex(random);
        redis.setValue(key(userId), sha256(token), TOKEN_TTL);
        return token;
    }

    @Override
    public void verify(Long userId, String authorizationToken) {
        if (userId == null || userId < 1 || authorizationToken == null
                || !authorizationToken.matches("[a-f0-9]{64}")) {
            throw authorizationExpired();
        }
        String storedDigest = redis.getValue(key(userId));
        if (storedDigest == null || !MessageDigest.isEqual(
                storedDigest.getBytes(StandardCharsets.US_ASCII),
                sha256(authorizationToken).getBytes(StandardCharsets.US_ASCII))) {
            throw authorizationExpired();
        }
    }

    @Override
    public void consume(Long userId, String authorizationToken) {
        if (userId == null || userId < 1 || authorizationToken == null
                || !authorizationToken.matches("[a-f0-9]{64}")) {
            throw authorizationExpired();
        }
        Long consumed = redis.executeScript(CONSUME_IF_MATCH, List.of(key(userId)), sha256(authorizationToken));
        if (!Long.valueOf(1).equals(consumed)) throw authorizationExpired();
    }

    private String key(Long userId) { return KEY_PREFIX + userId; }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("邮箱变更凭据计算失败", exception);
        }
    }

    private BusinessException invalidPassword() {
        return new BusinessException(CommonErrorCode.BAD_REQUEST, "登录密码不正确");
    }

    private BusinessException authorizationExpired() {
        return new BusinessException(CommonErrorCode.BAD_REQUEST, "邮箱变更验证已过期，请重新验证登录密码");
    }
}
