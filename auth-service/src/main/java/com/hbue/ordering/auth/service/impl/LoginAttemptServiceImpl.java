package com.hbue.ordering.auth.service.impl;

import com.hbue.ordering.auth.service.LoginAttemptService;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.redis.RedisOperationsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 基于 Redis 的登录失败计数和账号临时锁定实现。
 *
 * <p>失败计数按配置的固定时间窗口累计，达到阈值后写入带 TTL 的锁定 Key；Redis
 * 在多个认证服务实例之间共享状态。Redis 不可用时返回服务暂不可用，不允许认证流程
 * 绕过登录保护继续执行。用户名先统一去除首尾空白并转为小写，再计算 SHA-256 摘要，
 * 因此 Redis Key 不会直接暴露用户输入的账号。</p>
 *
 * @author order-system
 * @date 2026-10-14
 */
@Service
public class LoginAttemptServiceImpl implements LoginAttemptService {

    /**
     * 登录失败次数 Key 前缀。
     */
    private static final String FAILURE_KEY_PREFIX =
            "auth:login-attempt:failures:";

    /**
     * 登录临时锁定 Key 前缀。
     */
    private static final String LOCK_KEY_PREFIX =
            "auth:login-attempt:lock:";

    /**
     * Redis 基础操作组件。
     */
    private final RedisOperationsService redisOperationsService;

    /**
     * 触发账号锁定所需的最大连续失败次数。
     */
    private final int maxFailures;

    /**
     * 失败次数的固定累计窗口。
     */
    private final Duration failureWindow;

    /**
     * 达到失败阈值后的账号锁定时长。
     */
    private final Duration lockDuration;

    /**
     * 创建登录失败保护服务并读取可调整的策略配置。
     *
     * @param redisOperationsService Redis 基础操作组件
     * @param maxFailures 失败次数阈值，默认 5 次
     * @param failureWindow 失败次数累计窗口，默认 15 分钟
     * @param lockDuration 达到阈值后的锁定时长，默认 15 分钟
     * @throws IllegalArgumentException 阈值或时长配置不合法时抛出
     */
    public LoginAttemptServiceImpl(
            RedisOperationsService redisOperationsService,
            @Value("${security.login-attempt.max-failures:5}")
            int maxFailures,
            @Value("${security.login-attempt.failure-window:15m}")
            Duration failureWindow,
            @Value("${security.login-attempt.lock-duration:15m}")
            Duration lockDuration
    ) {
        // 拒绝无效策略，避免阈值为零或 TTL 无效导致账号被意外永久锁定。
        if (maxFailures < 1) {
            throw new IllegalArgumentException(
                    "登录失败次数阈值必须大于零"
            );
        }
        validateDuration(failureWindow, "失败次数累计窗口");
        validateDuration(lockDuration, "账号锁定时长");

        this.redisOperationsService = redisOperationsService;
        this.maxFailures = maxFailures;
        this.failureWindow = failureWindow;
        this.lockDuration = lockDuration;
    }

    /**
     * 检查账号当前是否处于临时锁定状态。
     *
     * @param username 登录用户名
     * @return Redis 中存在有效锁定 Key 时返回 true
     * @throws BusinessException
     *         Redis 不可用或无法确认锁定状态时返回服务暂不可用
     */
    @Override
    public boolean isLocked(String username) {
        // 查询与账号摘要对应的锁定 Key，不将原始用户名写入 Redis。
        String lockKey = buildKey(LOCK_KEY_PREFIX, username);
        try {
            Boolean locked = redisOperationsService.hasKey(lockKey);

            // Redis 返回 null 表示无法确认状态，必须按保护组件不可用处理。
            if (locked == null) {
                throw serviceUnavailableException();
            }
            return locked;
        } catch (DataAccessException exception) {
            // 限制状态无法读取时 fail-closed，防止 Redis 故障绕过账号保护。
            throw serviceUnavailableException();
        }
    }

    /**
     * 原子增加失败次数，并在达到阈值时创建临时锁定 Key。
     *
     * @param username 登录用户名
     * @return 本次失败是否达到阈值并触发锁定
     * @throws BusinessException
     *         Redis 不可用或无法确认计数结果时返回服务暂不可用
     */
    @Override
    public boolean recordFailure(String username) {
        // 失败窗口从第一次失败开始计算，后续请求不会延长窗口。
        String failureKey = buildKey(FAILURE_KEY_PREFIX, username);
        String lockKey = buildKey(LOCK_KEY_PREFIX, username);

        try {
            Long failureCount = redisOperationsService.increment(
                    failureKey,
                    failureWindow
            );

            // 无法确认计数结果时不能继续放行认证，否则会形成保护绕过。
            if (failureCount == null || failureCount < 1L) {
                throw serviceUnavailableException();
            }

            // 阈值未到时保留计数，后续失败仍在同一固定窗口中累计。
            if (failureCount < maxFailures) {
                return false;
            }

            // 达到阈值后写入独立的短期锁定 Key，使后续请求无需调用用户服务校验密码。
            redisOperationsService.setValue(
                    lockKey,
                    "1",
                    lockDuration
            );

            // 锁定时清除失败计数，防止锁定到期后旧计数立即再次触发锁定。
            redisOperationsService.delete(failureKey);
            return true;
        } catch (DataAccessException exception) {
            // Redis 写入失败时拒绝本次登录，不静默退化为无账号保护模式。
            throw serviceUnavailableException();
        }
    }

    /**
     * 登录凭据验证成功后清除此前的失败计数与锁定标记。
     *
     * @param username 登录用户名
     * @throws BusinessException
     *         Redis 不可用时返回服务暂不可用
     */
    @Override
    public void recordSuccess(String username) {
        // 成功认证意味着本账号凭据有效，清理两个状态 Key 以便后续从零开始计数。
        String failureKey = buildKey(FAILURE_KEY_PREFIX, username);
        String lockKey = buildKey(LOCK_KEY_PREFIX, username);

        try {
            redisOperationsService.delete(failureKey);
            redisOperationsService.delete(lockKey);
        } catch (DataAccessException exception) {
            // 无法完成状态清理时返回 503，避免客户端误以为登录保护状态已同步恢复。
            throw serviceUnavailableException();
        }
    }

    /**
     * 校验时长配置必须是至少 1 毫秒的正数。
     *
     * @param duration 待校验时长
     * @param settingName 配置项中文名称
     * @throws IllegalArgumentException 时长为空、为零或为负数时抛出
     */
    private void validateDuration(Duration duration, String settingName) {
        // Redis 的毫秒级过期命令要求 TTL 为正数，低于 1 毫秒会被截断为零。
        if (duration == null
                || duration.isNegative()
                || duration.isZero()
                || duration.toMillis() < 1L) {
            throw new IllegalArgumentException(
                    settingName + "必须至少为 1 毫秒"
            );
        }
    }

    /**
     * 生成不暴露原始用户名的 Redis Key。
     *
     * @param keyPrefix 计数或锁定状态对应的 Key 前缀
     * @param username 原始登录用户名
     * @return 前缀加规范化用户名 SHA-256 摘要组成的 Key
     * @throws BusinessException 用户名为空时返回参数错误
     * @throws IllegalStateException 当前 JDK 不支持 SHA-256 时抛出
     */
    private String buildKey(String keyPrefix, String username) {
        // 用户名在入口处已有非空校验，这里再次保护内部调用并统一大小写与首尾空白。
        if (username == null || username.isBlank()) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "用户名不能为空"
            );
        }

        String normalizedUsername = username.strip()
                .toLowerCase(Locale.ROOT);
        try {
            // 仅把摘要放入 Redis Key，避免 Redis 运维界面直接暴露用户账号。
            byte[] usernameDigest = MessageDigest.getInstance("SHA-256")
                    .digest(normalizedUsername.getBytes(StandardCharsets.UTF_8));
            return keyPrefix + HexFormat.of().formatHex(usernameDigest);
        } catch (NoSuchAlgorithmException exception) {
            // SHA-256 属于 Java 标准算法；缺失代表运行时环境异常，应明确失败。
            throw new IllegalStateException(
                    "当前运行环境不支持 SHA-256",
                    exception
            );
        }
    }

    /**
     * 创建统一的 Redis 保护组件不可用异常。
     *
     * @return 映射为 HTTP 503 的业务异常
     */
    private BusinessException serviceUnavailableException() {
        // 所有 Redis 保护状态故障都以相同错误码返回，不透露内部连接信息。
        return new BusinessException(
                CommonErrorCode.SERVICE_UNAVAILABLE,
                "登录保护服务暂时不可用，请稍后重试"
        );
    }
}
