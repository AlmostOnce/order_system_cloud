package com.hbue.ordering.auth.service.impl;

import com.hbue.ordering.auth.service.LoginAttemptService;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.redis.RedisOperationsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录失败计数和账号临时锁定服务测试。
 *
 * <p>使用 Redis 操作组件的测试替身检查 Key、阈值、TTL 和失败关闭语义，
 * 不依赖本机 Redis 实例。</p>
 *
 * @author order-system
 * @date 2026-10-14
 */
class LoginAttemptServiceImplTest {

    /**
     * 登录失败累计窗口。
     */
    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);

    /**
     * 账号达到失败阈值后的锁定时长。
     */
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    /**
     * Redis 基础操作测试替身。
     */
    private RedisOperationsService redisOperationsService;

    /**
     * 登录失败保护服务测试对象。
     */
    private LoginAttemptService loginAttemptService;

    /**
     * 初始化 Redis 替身和默认失败阈值配置。
     */
    @BeforeEach
    void setUp() {
        redisOperationsService = mock(RedisOperationsService.class);
        loginAttemptService = new LoginAttemptServiceImpl(
                redisOperationsService,
                5,
                FAILURE_WINDOW,
                LOCK_DURATION
        );
    }

    /**
     * 失败次数未达到阈值时应继续允许认证服务校验凭据。
     */
    @Test
    void shouldContinueAuthenticationWhenFailureCountIsBelowLimit() {
        when(redisOperationsService.increment(
                anyString(),
                eq(FAILURE_WINDOW)
        )).thenReturn(4L);

        // 记录第四次错误凭据，此时仍未达到五次锁定阈值。
        boolean locked = loginAttemptService.recordFailure("Test-User");

        // 确认本次失败没有触发锁定，累计窗口从首次失败开始计算。
        assertFalse(locked);
        verify(redisOperationsService).increment(
                anyString(),
                eq(FAILURE_WINDOW)
        );
    }

    /**
     * 达到失败次数阈值后应创建带 TTL 的锁定 Key 并清理失败计数。
     */
    @Test
    void shouldLockAccountWhenFailureCountReachesLimit() {
        when(redisOperationsService.increment(
                anyString(),
                eq(FAILURE_WINDOW)
        )).thenReturn(5L);

        // 记录第五次错误凭据，触发账号临时锁定。
        boolean locked = loginAttemptService.recordFailure("Test-User");

        // 确认锁定状态有 TTL，且用于计数的原始 Key 被清理。
        assertTrue(locked);
        verify(redisOperationsService).setValue(
                org.mockito.ArgumentMatchers.startsWith(
                        "auth:login-attempt:lock:"
                ),
                eq("1"),
                eq(LOCK_DURATION)
        );
        verify(redisOperationsService).delete(
                org.mockito.ArgumentMatchers.startsWith(
                        "auth:login-attempt:failures:"
                )
        );
    }

    /**
     * 查询锁定状态时应使用摘要 Key，而不是把原始用户名写入 Redis Key。
     */
    @Test
    void shouldCheckLockWithHashedUsernameKey() {
        when(redisOperationsService.hasKey(anyString())).thenReturn(true);

        // 查询用户名对应的锁定状态。
        boolean locked = loginAttemptService.isLocked("Test-User");

        // 确认账号已锁定，且 Redis Key 不包含原始用户名。
        assertTrue(locked);
        org.mockito.ArgumentCaptor<String> keyCaptor =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(redisOperationsService).hasKey(keyCaptor.capture());
        assertTrue(keyCaptor.getValue().startsWith(
                "auth:login-attempt:lock:"
        ));
        assertFalse(keyCaptor.getValue().contains("Test-User"));
    }

    /**
     * 凭据验证成功后应删除账号对应的失败计数和锁定状态。
     */
    @Test
    void shouldDeleteFailureAndLockKeysAfterSuccessfulAuthentication() {
        // 登录成功后同时清理失败计数和临时锁定 Key。
        loginAttemptService.recordSuccess("Test-User");

        // 两个 Key 均使用同一账号摘要，保证清理范围只影响当前账号。
        org.mockito.ArgumentCaptor<String> keyCaptor =
                org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(redisOperationsService, org.mockito.Mockito.times(2))
                .delete(keyCaptor.capture());
        String firstKey = keyCaptor.getAllValues().get(0);
        String secondKey = keyCaptor.getAllValues().get(1);
        String firstAccountDigest = firstKey.substring(
                firstKey.lastIndexOf(':') + 1
        );
        String secondAccountDigest = secondKey.substring(
                secondKey.lastIndexOf(':') + 1
        );
        assertNotEquals(firstKey, secondKey);
        assertEquals(firstAccountDigest, secondAccountDigest);
    }

    /**
     * Redis 无法写入失败计数时应拒绝认证，不允许绕过账号保护。
     */
    @Test
    void shouldFailClosedWhenRedisCannotRecordFailure() {
        when(redisOperationsService.increment(
                anyString(),
                eq(FAILURE_WINDOW)
        )).thenThrow(
                new DataAccessResourceFailureException("Redis unavailable")
        );

        // 记录失败时 Redis 不可用，保护服务应映射为服务暂不可用异常。
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> loginAttemptService.recordFailure("test-user")
        );

        // 不能把存储故障误报成普通密码错误并继续开放登录尝试。
        assertEquals(
                CommonErrorCode.SERVICE_UNAVAILABLE,
                exception.getErrorCode()
        );
    }

    /**
     * Redis 不可用时必须拒绝登录保护检查并返回服务暂不可用。
     */
    @Test
    void shouldFailClosedWhenRedisCannotReadLockState() {
        when(redisOperationsService.hasKey(anyString())).thenThrow(
                new DataAccessResourceFailureException("Redis unavailable")
        );

        // Redis 无法确认账号是否被锁定时，不能跳过保护继续验证密码。
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> loginAttemptService.isLocked("test-user")
        );

        // 对外返回 503，不泄露 Redis 连接异常的内部信息。
        assertEquals(
                CommonErrorCode.SERVICE_UNAVAILABLE,
                exception.getErrorCode()
        );
    }
}
