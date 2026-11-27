package com.hbue.ordering.user.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.redis.RedisOperationsService;
import com.hbue.ordering.user.dto.request.ChangeOwnPasswordRequest;
import com.hbue.ordering.user.mapper.UserMapper;
import com.hbue.ordering.user.model.UserDO;
import com.hbue.ordering.user.service.PasswordChangeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 已登录用户主动修改登录密码的业务实现。
 *
 * <p>核心流程：先校验新密码格式，再检查 Redis 失败计数是否达到上限，随后在
 * 本地事务中用 {@code SELECT ... FOR UPDATE} 锁定凭据行，使用 BCrypt 校验
 * 当前密码，确认新旧密码不同后按凭据版本条件更新密码并递增版本。版本递增使
 * 旧 JWT 与刷新令牌立即失效，用户需要用新密码重新登录。</p>
 *
 * <p>安全约束：</p>
 * <ul>
 *   <li>失败计数放在 Redis 而不是数据库，失败请求即使因异常回滚事务也不会
 *       撤销计数，保证爆破防护不被事务语义抵消。</li>
 *   <li>Redis 不可用时按拒绝处理（fail-closed），宁可暂时无法改密，也不在
 *       失去限流保护的情况下允许在线尝试当前密码。</li>
 *   <li>成功后清理失败计数；清理失败只记录日志，不回滚已生效的改密结果，
 *       计数最迟由 TTL 到期自然失效。</li>
 * </ul>
 *
 * @author order-system
 * @date 2026-11-23
 */
@Service
public class PasswordChangeServiceImpl implements PasswordChangeService {

    /**
     * 日志对象。
     */
    private static final Logger log =
            LoggerFactory.getLogger(PasswordChangeServiceImpl.class);

    /**
     * 失败计数键前缀。
     */
    private static final String FAIL_KEY_PREFIX = "user:password-change:fail:";

    /**
     * 新密码允许的最小字符数，与注册、找回密码口径保持一致。
     */
    private static final int MIN_PASSWORD_LENGTH = 6;

    /**
     * 新密码允许的最大字符数，与注册、找回密码口径保持一致。
     */
    private static final int MAX_PASSWORD_LENGTH = 64;

    /**
     * 新密码允许的最大 UTF-8 字节数；BCrypt 只处理前 72 字节，超出部分会被截断，
     * 必须显式拒绝，避免用户误以为更长的密码后缀生效。
     */
    private static final int MAX_PASSWORD_BYTES = 72;

    /**
     * 用户数据访问接口。
     */
    private final UserMapper userMapper;

    /**
     * 密码加密器，负责 BCrypt 明文比对与新哈希生成。
     */
    private final PasswordEncoder passwordEncoder;

    /**
     * Redis 基础操作服务，用于失败计数。
     */
    private final RedisOperationsService redisOperationsService;

    /**
     * 窗口期内允许的当前密码错误次数上限。
     */
    private final int maxFailures;

    /**
     * 失败计数窗口时长。
     */
    private final Duration failureTtl;

    /**
     * 创建修改密码业务服务。
     *
     * @param userMapper             用户数据访问接口
     * @param passwordEncoder        密码加密器
     * @param redisOperationsService Redis 基础操作服务
     * @param maxFailures            窗口期内允许的失败次数上限
     * @param failureTtlSeconds      失败计数窗口秒数
     */
    public PasswordChangeServiceImpl(
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            RedisOperationsService redisOperationsService,
            @Value("${security.password-change.max-failures:5}")
            int maxFailures,
            @Value("${security.password-change.failure-ttl-seconds:900}")
            long failureTtlSeconds
    ) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.redisOperationsService = redisOperationsService;
        this.maxFailures = maxFailures;
        this.failureTtl = Duration.ofSeconds(failureTtlSeconds);
    }

    /**
     * 校验当前密码后修改本人登录密码。
     *
     * <p>事务只覆盖数据库读写；Redis 计数不受事务管理，失败计数在异常回滚后
     * 仍然保留，这是有意设计。行锁保证同一账号的并发改密串行执行，条件更新
     * 再兜底一次版本校验，后到请求不会覆盖先到结果。</p>
     *
     * @param userId  当前登录用户编号
     * @param request 当前密码与新密码
     * @throws BusinessException 未登录、参数不合法、当前密码错误、失败次数超限、
     *                           账号不可用或并发改密冲突时抛出
     */
    @Override
    @Transactional
    public void changeOwnPassword(Long userId, ChangeOwnPasswordRequest request) {
        if (userId == null) {
            throw new BusinessException(
                    CommonErrorCode.UNAUTHORIZED,
                    "未登录或登录已过期"
            );
        }

        String currentPassword = request == null ? null : request.currentPassword();
        String newPassword = request == null ? null : request.newPassword();
        if (currentPassword == null || currentPassword.isEmpty()) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "请输入当前登录密码"
            );
        }
        // 注册与找回密码都限制密码不超过 72 个 UTF-8 字节，超长输入不可能是
        // 有效密码；提前拒绝可避免 BCrypt 对超长明文抛异常而变成 500。
        if (currentPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "当前密码不正确"
            );
        }
        // 新密码先做纯格式校验，不触碰数据库与 Redis，避免非法请求消耗防护资源。
        if (newPassword == null
                || newPassword.length() < MIN_PASSWORD_LENGTH
                || newPassword.length() > MAX_PASSWORD_LENGTH
                || newPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "新密码须为6至64个字符，UTF-8编码不超过72字节"
            );
        }

        // 达到上限时直接拒绝，不再加锁和比对密码，避免锁竞争与哈希计算被滥用。
        requireNotRateLimited(userId);

        // 锁定凭据行：读取当前密码哈希与版本，防止并发改密互相覆盖。
        UserDO current = userMapper.selectPasswordCredentialForUpdate(userId);
        if (current == null) {
            throw new BusinessException(
                    CommonErrorCode.NOT_FOUND,
                    "用户不存在或已停用"
            );
        }

        if (!passwordEncoder.matches(currentPassword, current.getPasswordHash())) {
            // 先计数再抛出异常：事务回滚不会撤销 Redis 计数，防护持续有效。
            long failures = incrementFailures(userId);
            if (failures >= maxFailures) {
                throw new BusinessException(
                        CommonErrorCode.TOO_MANY_REQUESTS,
                        "当前密码错误次数过多，请稍后再试"
                );
            }
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "当前密码不正确，还可尝试 " + (maxFailures - failures) + " 次"
            );
        }

        // BCrypt 使用随机盐，新旧密码是否相同必须用明文与现有哈希比对，
        // 不能比较两次生成的哈希值。
        if (passwordEncoder.matches(newPassword, current.getPasswordHash())) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "新密码不能与当前密码相同"
            );
        }

        // 按版本条件更新，凭据版本递增使旧访问令牌与刷新令牌同时失效。
        int updated = userMapper.resetLoginPassword(
                userId,
                current.getEmail(),
                current.getCredentialVersion(),
                passwordEncoder.encode(newPassword)
        );
        if (updated != 1) {
            throw new BusinessException(
                    CommonErrorCode.CONFLICT,
                    "账号状态已变化，请重新登录后重试"
            );
        }

        clearFailures(userId);
        log.info("学生修改登录密码成功：userId={}", userId);
    }

    /**
     * 检查当前用户是否已超过当前密码错误次数上限。
     *
     * @param userId 用户编号
     * @throws BusinessException 已超限时返回 429；Redis 不可用返回 503
     */
    private void requireNotRateLimited(Long userId) {
        try {
            String value = redisOperationsService.getValue(failKey(userId));
            long failures = parseFailures(value);
            if (failures >= maxFailures) {
                throw new BusinessException(
                        CommonErrorCode.TOO_MANY_REQUESTS,
                        "当前密码错误次数过多，请稍后再试"
                );
            }
        } catch (BusinessException exception) {
            // 业务拒绝直接向上抛出，不能被下面的兜底分支改写为 503。
            throw exception;
        } catch (RuntimeException exception) {
            // Redis 不可用时无法确认失败次数，按拒绝处理，避免防护失效。
            log.error("查询修改密码失败计数失败：userId={}", userId, exception);
            throw new BusinessException(
                    CommonErrorCode.SERVICE_UNAVAILABLE,
                    "密码修改暂时不可用，请稍后重试"
            );
        }
    }

    /**
     * 累计一次当前密码校验失败。
     *
     * <p>使用带首次过期时间的原子自增，后续失败不刷新 TTL，保证计数窗口固定，
     * 持续失败不会无限延长封禁时间。</p>
     *
     * @param userId 用户编号
     * @return 累计后的失败次数
     * @throws BusinessException Redis 不可用或未返回计数时抛出 503
     */
    private long incrementFailures(Long userId) {
        try {
            Long failures = redisOperationsService.increment(
                    failKey(userId),
                    failureTtl
            );
            if (failures == null) {
                // 未拿到计数就无法判断是否超限，按不可用处理而不是放行。
                throw new BusinessException(
                        CommonErrorCode.SERVICE_UNAVAILABLE,
                        "密码修改暂时不可用，请稍后重试"
                );
            }
            return failures;
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.error("累计修改密码失败次数失败：userId={}", userId, exception);
            throw new BusinessException(
                    CommonErrorCode.SERVICE_UNAVAILABLE,
                    "密码修改暂时不可用，请稍后重试"
            );
        }
    }

    /**
     * 清空当前用户的失败计数。
     *
     * <p>清理失败不影响已生效的改密结果，只记录告警，最迟由 TTL 到期失效。</p>
     *
     * @param userId 用户编号
     */
    private void clearFailures(Long userId) {
        try {
            redisOperationsService.delete(failKey(userId));
        } catch (RuntimeException exception) {
            log.warn("清理修改密码失败计数失败：userId={}", userId);
        }
    }

    /**
     * 解析失败计数值，缺失或格式异常按 0 处理，避免旧数据阻断正常改密。
     *
     * @param value Redis 中保存的计数值
     * @return 非负失败次数
     */
    private long parseFailures(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Math.max(0L, Long.parseLong(value));
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    /**
     * 生成失败计数键。
     *
     * @param userId 用户编号
     * @return Redis 键
     */
    private String failKey(Long userId) {
        return FAIL_KEY_PREFIX + userId;
    }
}
