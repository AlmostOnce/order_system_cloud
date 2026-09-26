package com.hbue.ordering.auth.service;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Refresh Token 分布式锁服务测试。
 *
 * @author order-system
 * @date 2026-09-25
 */
class RefreshTokenLockServiceTest {

    /**
     * 验证锁 Key 使用 Refresh Token 的 SHA-256 摘要，且释放已获取的锁。
     */
    @Test
    void shouldUseHashedTokenAsLockKeyAndReleaseLock() throws Exception {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock distributedLock = mock(RLock.class);
        String expectedLockKey = "auth:refresh-token:lock:"
                + "66e4f4e9739a9ef9a9d6e414cfd05780c4ab0eb03e21fbf90ebf87e76d4db8f6";

        when(redissonClient.getLock(expectedLockKey))
                .thenReturn(distributedLock);
        when(distributedLock.tryLock(5L, 30L, TimeUnit.SECONDS))
                .thenReturn(true);
        when(distributedLock.isHeldByCurrentThread())
                .thenReturn(true);

        RefreshTokenLockService lockService =
                new RefreshTokenLockService(redissonClient);

        RefreshTokenLockService.LockHandle lockHandle =
                lockService.acquire("old-refresh-token");
        lockHandle.close();

        verify(redissonClient).getLock(expectedLockKey);
        verify(distributedLock).tryLock(5L, 30L, TimeUnit.SECONDS);
        verify(distributedLock).unlock();
    }

    /**
     * 验证等待锁超时后返回服务不可用，且不会释放未获取的锁。
     */
    @Test
    void shouldReturnServiceUnavailableWhenLockWaitTimesOut() throws Exception {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock distributedLock = mock(RLock.class);
        when(redissonClient.getLock(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(distributedLock);
        when(distributedLock.tryLock(5L, 30L, TimeUnit.SECONDS))
                .thenReturn(false);

        RefreshTokenLockService lockService =
                new RefreshTokenLockService(redissonClient);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> lockService.acquire("old-refresh-token")
        );

        assertEquals(
                CommonErrorCode.SERVICE_UNAVAILABLE,
                exception.getErrorCode()
        );
        verify(distributedLock, never()).unlock();
    }

    /**
     * 验证锁等待线程被中断时恢复中断标记并返回服务不可用。
     */
    @Test
    void shouldRestoreInterruptFlagWhenLockWaitIsInterrupted()
            throws Exception {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock distributedLock = mock(RLock.class);
        when(redissonClient.getLock(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(distributedLock);
        when(distributedLock.tryLock(5L, 30L, TimeUnit.SECONDS))
                .thenThrow(new InterruptedException("test interrupt"));

        RefreshTokenLockService lockService =
                new RefreshTokenLockService(redissonClient);

        try {
            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> lockService.acquire("old-refresh-token")
            );

            assertEquals(
                    CommonErrorCode.SERVICE_UNAVAILABLE,
                    exception.getErrorCode()
            );
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            // 清理当前测试线程的中断状态，避免影响后续测试。
            Thread.interrupted();
        }
    }

    /**
     * 验证空 Refresh Token 在访问 Redisson 前被拒绝。
     */
    @Test
    void shouldRejectBlankTokenWithoutCallingRedisson() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RefreshTokenLockService lockService =
                new RefreshTokenLockService(redissonClient);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> lockService.acquire(" ")
        );

        assertEquals(
                CommonErrorCode.UNAUTHORIZED,
                exception.getErrorCode()
        );
        verify(redissonClient, never())
                .getLock(org.mockito.ArgumentMatchers.anyString());
    }

    /**
     * 验证 Redis 释放锁失败不会覆盖已经完成的业务结果。
     */
    @Test
    void shouldNotPropagateRedisFailureWhenReleasingLock() throws Exception {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock distributedLock = mock(RLock.class);
        when(redissonClient.getLock(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(distributedLock);
        when(distributedLock.tryLock(5L, 30L, TimeUnit.SECONDS))
                .thenReturn(true);
        when(distributedLock.isHeldByCurrentThread())
                .thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("Redis unavailable"))
                .when(distributedLock)
                .unlock();

        RefreshTokenLockService lockService =
                new RefreshTokenLockService(redissonClient);
        RefreshTokenLockService.LockHandle lockHandle =
                lockService.acquire("old-refresh-token");

        assertDoesNotThrow(lockHandle::close);
        verify(distributedLock).unlock();
    }
}
