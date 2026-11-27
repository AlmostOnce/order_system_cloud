package com.hbue.ordering.user.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.redis.RedisOperationsService;
import com.hbue.ordering.user.api.vo.AccountDeletionCancelTokenVO;
import com.hbue.ordering.user.service.EmailCodeSender;
import com.hbue.ordering.user.vo.AccountDeletionAuthorizationVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

/**
 * 账号注销的邮箱验证码、一次性授权令牌和撤回令牌管理。
 *
 * <p>所有凭据都只保存在 Redis，并且只保存摘要；验证码绑定用户与目标邮箱，
 * 错误次数用尽或校验成功即删除。注销授权令牌 10 分钟内有效且只能使用一次，
 * 撤回令牌同样一次性且 10 分钟过期。本类不读写数据库，用户状态校验由调用方在
 * 事务内完成，避免凭据校验与业务状态判断混在一起。</p>
 *
 * @author order-system
 * @date 2026-11-24
 */
@Service
public class AccountDeletionVerificationService {

    /**
     * 验证码错误次数上限；达到上限后凭据立即删除，必须重新发送。
     */
    private static final int MAX_CODE_ATTEMPTS = 5;

    /**
     * 验证码有效期。
     */
    private static final Duration CODE_TTL = Duration.ofMinutes(10);

    /**
     * 同一用户两次发送验证码的最小间隔。
     */
    private static final Duration SEND_INTERVAL = Duration.ofSeconds(60);

    /**
     * 同一用户 10 分钟内最多发送次数。
     */
    private static final int SEND_WINDOW_LIMIT = 5;

    /**
     * 一次性授权令牌与撤回令牌的有效期。
     */
    private static final Duration TOKEN_TTL = Duration.ofMinutes(10);

    /**
     * 令牌长度（十六进制字符数），对应 32 字节随机数。
     */
    private static final int TOKEN_HEX_LENGTH = 64;

    /**
     * 安全随机数生成器。
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 校验并消费验证码的 Lua 脚本。
     *
     * <p>比较摘要与累加错误次数在同一原子操作中完成，避免并发尝试绕过次数限制；
     * 匹配成功立即删除，错误次数达到上限也删除。</p>
     */
    private static final DefaultRedisScript<Long> CONSUME_CODE = new DefaultRedisScript<>("""
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

    /**
     * 抢占分布式租约：仅当 Key 不存在时写入并设置过期时间。
     */
    private static final DefaultRedisScript<Long> ACQUIRE_LEASE = new DefaultRedisScript<>("""
            local ok = redis.call('SET', KEYS[1], ARGV[1], 'NX', 'EX', ARGV[2])
            if ok then return 1 else return 0 end
            """, Long.class);

    /**
     * 释放租约：只有仍持有同一租约标识时才删除，避免误删其他实例的新租约。
     */
    private static final DefaultRedisScript<Long> RELEASE_LEASE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    /**
     * Redis 基础操作组件。
     */
    private final RedisOperationsService redis;

    /**
     * 邮件发送边界。
     */
    private final EmailCodeSender sender;

    /**
     * 验证码摘要密钥；为空时拒绝发送与校验，避免使用可预测的摘要。
     */
    private final String secret;

    /**
     * 创建注销凭据服务。
     *
     * @param redis   Redis 操作组件
     * @param sender  邮件发送边界
     * @param secret  验证码 HMAC 密钥
     */
    public AccountDeletionVerificationService(RedisOperationsService redis, EmailCodeSender sender,
                                              @Value("${email.verification.secret:}") String secret) {
        this.redis = redis;
        this.sender = sender;
        this.secret = secret;
    }

    /**
     * 向已绑定邮箱发送注销验证码。
     *
     * <p>发送频率限制为 60 秒一次、10 分钟最多 5 次。邮件发送失败时删除刚写入的
     * 验证码并释放 60 秒间隔，允许用户立即重试；10 分钟窗口计数仍保留，防止借失败重试
     * 无限触发 SMTP。</p>
     *
     * @param userId 用户 ID
     * @param email  已绑定并验证的邮箱
     */
    public void sendCode(Long userId, String email) {
        requireSecret();
        // 同一用户 60 秒内只允许发送一次；INCR 首次写入时设置 TTL。
        Long intervalCount = redis.increment(sendIntervalKey(userId), SEND_INTERVAL);
        if (intervalCount == null || intervalCount > 1) {
            throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS, "验证码已发送，请60秒后再试");
        }

        // 固定 10 分钟窗口内最多 5 次，防止短信/邮件轰炸。
        Long windowCount = redis.increment(sendWindowKey(userId), CODE_TTL);
        if (windowCount == null || windowCount > SEND_WINDOW_LIMIT) {
            throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS, "验证码发送过于频繁，请稍后再试");
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        String digest = digestCode(userId, email, code);
        redis.setValue(codeKey(userId), digest + ":0", CODE_TTL);
        try {
            sender.sendAccountDeletion(email, code);
        } catch (RuntimeException exception) {
            // 发送失败不应让用户等待 60 秒，也不能留下未送达的验证码。
            redis.delete(codeKey(userId));
            redis.delete(sendIntervalKey(userId));
            throw exception;
        }
    }

    /**
     * 校验验证码并签发一次性注销授权令牌。
     *
     * @param userId 用户 ID
     * @param email  当前绑定邮箱，必须与发送验证码时一致
     * @param code   用户输入的 6 位验证码
     * @return 授权令牌及有效秒数
     */
    public AccountDeletionAuthorizationVO authorize(Long userId, String email, String code) {
        requireSecret();
        Long consumed = redis.executeScript(
                CONSUME_CODE,
                List.of(codeKey(userId)),
                digestCode(userId, email, code),
                String.valueOf(MAX_CODE_ATTEMPTS)
        );
        if (consumed == null || consumed != 1L) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "验证码错误或已过期，请重新获取");
        }

        String token = randomToken();
        redis.setValue(authorizationKey(userId), sha256(token), TOKEN_TTL);
        return new AccountDeletionAuthorizationVO(token, TOKEN_TTL.toSeconds());
    }

    /**
     * 判断授权令牌是否仍有效。
     *
     * <p>只做比较，不删除；提交事务成功后再调用 {@link #revokeAuthorizationToken(Long)}，
     * 避免提交失败时令牌已被消费而用户必须重新获取验证码。</p>
     *
     * @param userId 用户 ID
     * @param token  客户端提交的授权令牌
     * @return 令牌与当前用户绑定且未过期时返回 true
     */
    public boolean isAuthorizationTokenValid(Long userId, String token) {
        if (token == null || token.length() != TOKEN_HEX_LENGTH) {
            return false;
        }
        String stored = redis.getValue(authorizationKey(userId));
        return stored != null && stored.equals(sha256(token));
    }

    /**
     * 删除已使用的注销授权令牌。
     *
     * @param userId 用户 ID
     */
    public void revokeAuthorizationToken(Long userId) {
        redis.delete(authorizationKey(userId));
    }

    /**
     * 为待注销账号签发一次性撤回令牌。
     *
     * @param userId 用户 ID
     * @return 撤回令牌及有效秒数
     */
    public AccountDeletionCancelTokenVO issueCancelToken(Long userId) {
        String token = randomToken();
        // 以令牌摘要作为 Key，服务端无法从 Key 反推出令牌明文。
        redis.setValue(cancelKey(token), String.valueOf(userId), TOKEN_TTL);
        return new AccountDeletionCancelTokenVO(token, TOKEN_TTL.toSeconds());
    }

    /**
     * 原子消费撤回令牌并返回所属用户。
     *
     * @param token 撤回令牌明文
     * @return 用户 ID；令牌不存在、已使用或格式错误时返回 null
     */
    public Long consumeCancelToken(String token) {
        if (token == null || token.length() != TOKEN_HEX_LENGTH) {
            return null;
        }
        String value = redis.getAndDelete(cancelKey(token));
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            // 值被篡改时按无效令牌处理，不泄露内部细节。
            return null;
        }
    }

    /**
     * 尝试抢占到期处理的分布式租约。
     *
     * @param userId      用户 ID
     * @param leaseId     本次处理的唯一租约标识，用于安全释放
     * @param leaseSeconds 租约有效秒数
     * @return 抢占成功返回 true；其他实例正在处理时返回 false
     */
    public boolean tryAcquireLease(Long userId, String leaseId, long leaseSeconds) {
        Long result = redis.executeScript(
                ACQUIRE_LEASE,
                List.of(leaseKey(userId)),
                leaseId,
                String.valueOf(leaseSeconds)
        );
        return result != null && result == 1L;
    }

    /**
     * 释放到期处理租约。
     *
     * @param userId  用户 ID
     * @param leaseId 本次处理的租约标识
     */
    public void releaseLease(Long userId, String leaseId) {
        redis.executeScript(RELEASE_LEASE, List.of(leaseKey(userId)), leaseId);
    }

    /**
     * 生成 64 位十六进制随机令牌。
     *
     * @return 令牌明文
     */
    private String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * 计算验证码摘要，绑定用户与目标邮箱。
     *
     * @param userId 用户 ID
     * @param email  目标邮箱
     * @param code   验证码
     * @return HMAC-SHA256 十六进制摘要
     */
    private String digestCode(Long userId, String email, String code) {
        requireSecret();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(
                    (userId + "|" + email + "|" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            // 密钥算法不可用时属于部署错误，不把底层异常暴露给客户端。
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "验证服务暂不可用");
        }
    }

    /**
     * 计算普通 SHA-256 摘要，用于令牌存储。
     *
     * @param value 原文
     * @return 十六进制摘要
     */
    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "验证服务暂不可用");
        }
    }

    /**
     * 校验验证码密钥已配置。
     */
    private void requireSecret() {
        if (secret == null || secret.isBlank()) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "邮件验证服务尚未配置");
        }
    }

    private String codeKey(Long userId) {
        return "account:deletion:code:" + userId;
    }

    private String sendIntervalKey(Long userId) {
        return "account:deletion:code:interval:" + userId;
    }

    private String sendWindowKey(Long userId) {
        return "account:deletion:code:window:" + userId;
    }

    private String authorizationKey(Long userId) {
        return "account:deletion:authorization:" + userId;
    }

    private String cancelKey(String token) {
        return "account:deletion:cancel:" + sha256(token);
    }

    private String leaseKey(Long userId) {
        return "account:deletion:lease:" + userId;
    }
}
