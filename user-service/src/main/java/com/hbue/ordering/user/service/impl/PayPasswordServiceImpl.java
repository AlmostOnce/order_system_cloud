package com.hbue.ordering.user.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.redis.RedisOperationsService;
import com.hbue.ordering.user.dto.request.PayPasswordSetRequest;
import com.hbue.ordering.user.mapper.UserMapper;
import com.hbue.ordering.user.model.UserDO;
import com.hbue.ordering.user.service.PayPasswordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 支付密码业务实现。
 *
 * <p>校验失败的计数与锁定采用 Redis 带 TTL 的计数器实现：失败计数在
 * 窗口期内累加不清零，达到阈值后写入锁定键，锁定期内一律拒绝；
 * 校验成功即删除失败计数。计数与锁定都是临时状态，因此不入库。</p>
 *
 * <p>Redis 不可用时按拒绝处理（fail-closed），避免防护失效时被暴力破解。</p>
 *
 * @author order-system
 * @date 2026-10-14
 */
@Service
public class PayPasswordServiceImpl implements PayPasswordService {

    /** 日志对象。 */
    private static final Logger log =
            LoggerFactory.getLogger(PayPasswordServiceImpl.class);

    /** 失败计数键前缀。 */
    private static final String FAIL_KEY_PREFIX = "user:pay-password:fail:";

    /** 锁定标记键前缀。 */
    private static final String LOCK_KEY_PREFIX = "user:pay-password:lock:";

    /** 用户启用状态。 */
    private static final Integer USER_STATUS_ENABLED = 1;

    /** 用户数据访问接口。 */
    private final UserMapper userMapper;

    /** 密码加密器。 */
    private final PasswordEncoder passwordEncoder;

    /** Redis 基础操作服务。 */
    private final RedisOperationsService redisOperationsService;

    /** 允许的连续失败次数上限。 */
    private final int maxFailures;

    /** 失败计数保留时长。 */
    private final Duration failureTtl;

    /** 触发上限后的锁定时长。 */
    private final Duration lockTtl;

    /**
     * 创建支付密码业务服务。
     *
     * @param userMapper 用户数据访问接口
     * @param passwordEncoder 密码加密器
     * @param redisOperationsService Redis 基础操作服务
     * @param maxFailures 允许的连续失败次数上限
     * @param failureTtlSeconds 失败计数保留秒数
     * @param lockTtlSeconds 锁定时长秒数
     */
    public PayPasswordServiceImpl(
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            RedisOperationsService redisOperationsService,
            @Value("${security.pay-password.max-failures:5}")
            int maxFailures,
            @Value("${security.pay-password.failure-ttl-seconds:900}")
            long failureTtlSeconds,
            @Value("${security.pay-password.lock-ttl-seconds:900}")
            long lockTtlSeconds
    ) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.redisOperationsService = redisOperationsService;
        this.maxFailures = maxFailures;
        this.failureTtl = Duration.ofSeconds(failureTtlSeconds);
        this.lockTtl = Duration.ofSeconds(lockTtlSeconds);
    }

    /**
     * 首次设置或修改支付密码。
     *
     * @param userId 用户 ID
     * @param request 支付密码设置请求
     */
    @Override
    public void setPayPassword(Long userId, PayPasswordSetRequest request) {
        if (userId == null) {
            throw new BusinessException(
                    CommonErrorCode.UNAUTHORIZED,
                    "未登录或登录已过期"
            );
        }
        if (request == null || request.getNewPayPassword() == null
                || request.getNewPayPassword().isBlank()) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "支付密码不能为空"
            );
        }

        UserDO user = requireEnabledUser(userId);
        String newPayPassword = request.getNewPayPassword();
        String existingHash = user.getPayPasswordHash();

        if (existingHash == null || existingHash.isBlank()) {
            // 首次设置：支付密码是第二道凭据，设置动作本身必须由登录密码确认身份。
            if (request.getLoginPassword() == null
                    || request.getLoginPassword().isBlank()) {
                throw new BusinessException(
                        CommonErrorCode.BAD_REQUEST,
                        "首次设置支付密码需要验证登录密码"
                );
            }
            if (!passwordEncoder.matches(
                    request.getLoginPassword(),
                    user.getPasswordHash())) {
                throw new BusinessException(
                        CommonErrorCode.BAD_REQUEST,
                        "登录密码不正确"
                );
            }
            if (passwordEncoder.matches(newPayPassword, user.getPasswordHash())) {
                throw new BusinessException(
                        CommonErrorCode.BAD_REQUEST,
                        "支付密码不得与登录密码相同"
                );
            }
        } else {
            // 修改：必须持有原支付密码，复用校验逻辑享受同等防爆破保护。
            if (request.getOldPayPassword() == null
                    || request.getOldPayPassword().isBlank()) {
                throw new BusinessException(
                        CommonErrorCode.BAD_REQUEST,
                        "修改支付密码需要提供原支付密码"
                );
            }
            verifyPayPassword(userId, request.getOldPayPassword());
            if (passwordEncoder.matches(newPayPassword, existingHash)) {
                throw new BusinessException(
                        CommonErrorCode.BAD_REQUEST,
                        "新支付密码不得与原支付密码相同"
                );
            }
        }

        userMapper.updatePayPassword(
                userId,
                passwordEncoder.encode(newPayPassword)
        );
        // 设置成功后清空失败计数，避免旧计数影响后续使用。
        clearFailures(userId);
    }

    /**
     * 校验支付密码，失败时累计计数并在超过阈值后锁定。
     *
     * @param userId 用户 ID
     * @param payPassword 支付密码明文
     */
    @Override
    public void verifyPayPassword(Long userId, String payPassword) {
        if (userId == null) {
            throw new BusinessException(
                    CommonErrorCode.UNAUTHORIZED,
                    "未登录或登录已过期"
            );
        }
        if (payPassword == null || payPassword.isBlank()) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "支付密码不能为空"
            );
        }

        // 锁定优先于密码比对，锁定期内不再消耗比对次数。
        if (isLocked(userId)) {
            throw new BusinessException(
                    CommonErrorCode.TOO_MANY_REQUESTS,
                    "支付密码错误次数过多，请稍后再试"
            );
        }

        UserDO user = requireEnabledUser(userId);
        String payPasswordHash = user.getPayPasswordHash();
        if (payPasswordHash == null || payPasswordHash.isBlank()) {
            throw new BusinessException(
                    CommonErrorCode.CONFLICT,
                    "尚未设置支付密码，请先设置后再使用余额"
            );
        }

        if (passwordEncoder.matches(payPassword, payPasswordHash)) {
            clearFailures(userId);
            return;
        }

        long failures = incrementFailures(userId);
        if (failures >= maxFailures) {
            lock(userId);
            throw new BusinessException(
                    CommonErrorCode.TOO_MANY_REQUESTS,
                    "支付密码错误次数过多，账号已锁定，请稍后再试"
            );
        }

        long remaining = maxFailures - failures;
        throw new BusinessException(
                CommonErrorCode.BAD_REQUEST,
                "支付密码错误，还可尝试 " + remaining + " 次"
        );
    }

    /**
     * 查询用户是否已设置支付密码。
     *
     * @param userId 用户 ID
     * @return 已设置时返回 true
     */
    @Override
    public boolean isPayPasswordSet(Long userId) {
        if (userId == null) {
            return false;
        }
        UserDO user = userMapper.selectBalanceById(userId);
        return user != null
                && user.getPayPasswordHash() != null
                && !user.getPayPasswordHash().isBlank();
    }

    /**
     * 查询启用且未删除的用户，不存在时按资源不存在处理。
     *
     * @param userId 用户 ID
     * @return 用户持久化对象
     */
    private UserDO requireEnabledUser(Long userId) {
        UserDO user = userMapper.selectBalanceById(userId);
        if (user == null || !USER_STATUS_ENABLED.equals(user.getStatus())) {
            throw new BusinessException(
                    CommonErrorCode.NOT_FOUND,
                    "用户不存在"
            );
        }
        return user;
    }

    /**
     * 判断用户当前是否处于支付密码锁定期。
     *
     * @param userId 用户 ID
     * @return 锁定中返回 true
     */
    private boolean isLocked(Long userId) {
        try {
            return Boolean.TRUE.equals(
                    redisOperationsService.hasKey(lockKey(userId))
            );
        } catch (RuntimeException exception) {
            // Redis 不可用时无法确认锁定状态，按拒绝处理，避免防护失效。
            log.error("查询支付密码锁定状态失败：userId={}", userId, exception);
            throw new BusinessException(
                    CommonErrorCode.SERVICE_UNAVAILABLE,
                    "支付密码校验暂时不可用，请稍后重试"
            );
        }
    }

    /**
     * 累计一次支付密码校验失败。
     *
     * @param userId 用户 ID
     * @return 累计后的失败次数
     */
    private long incrementFailures(Long userId) {
        try {
            String key = failKey(userId);
            Long failures = redisOperationsService.increment(key);
            long current = failures == null ? 0L : failures;
            if (current == 1L) {
                // 只在首次计数时设置 TTL，避免每次失败都重置过期时间。
                redisOperationsService.expire(key, failureTtl);
            }
            return current;
        } catch (RuntimeException exception) {
            log.error("累计支付密码失败次数失败：userId={}", userId, exception);
            throw new BusinessException(
                    CommonErrorCode.SERVICE_UNAVAILABLE,
                    "支付密码校验暂时不可用，请稍后重试"
            );
        }
    }

    /**
     * 写入支付密码锁定标记。
     *
     * @param userId 用户 ID
     */
    private void lock(Long userId) {
        try {
            redisOperationsService.setValue(
                    lockKey(userId),
                    "1",
                    lockTtl
            );
        } catch (RuntimeException exception) {
            // 锁定写入失败时仍拒绝本次请求，最迟由计数窗口兜底。
            log.error("写入支付密码锁定标记失败：userId={}", userId, exception);
        }
    }

    /**
     * 清空支付密码失败计数。
     *
     * @param userId 用户 ID
     */
    private void clearFailures(Long userId) {
        try {
            redisOperationsService.delete(failKey(userId));
        } catch (RuntimeException exception) {
            // 清理失败不影响设置或校验结果，最迟由 TTL 到期失效。
            log.warn("清理支付密码失败计数失败：userId={}", userId);
        }
    }

    /**
     * 生成失败计数键。
     *
     * @param userId 用户 ID
     * @return Redis 键
     */
    private String failKey(Long userId) {
        return FAIL_KEY_PREFIX + userId;
    }

    /**
     * 生成锁定标记键。
     *
     * @param userId 用户 ID
     * @return Redis 键
     */
    private String lockKey(Long userId) {
        return LOCK_KEY_PREFIX + userId;
    }
}
