package com.hbue.ordering.common.redis;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 定时任务互斥租约测试：验证多实例互斥、安全释放与 Redis 故障时的降级。
 *
 * @author order-system
 * @date 2026-11-27
 */
class ScheduledTaskLockTest {

    /**
     * 测试用租约时长。
     */
    private static final Duration LEASE = Duration.ofMinutes(5);

    /**
     * 其它实例持锁时必须返回 null，且不能误删他人的租约。
     */
    @Test
    void shouldSkipWhenAnotherInstanceHoldsLease() {
        RedisOperationsService redis = mock(RedisOperationsService.class);
        when(redis.setIfAbsent(anyString(), anyString(), eq(LEASE))).thenReturn(false);

        assertNull(new ScheduledTaskLock(redis).tryAcquire("sample-task", LEASE));
        verify(redis, never()).deleteIfEquals(anyString(), anyString());
    }

    /**
     * Redis 不可用时返回 null，任务跳过本轮而不是退化为多实例并行。
     */
    @Test
    void shouldSkipWhenRedisIsUnavailable() {
        RedisOperationsService redis = mock(RedisOperationsService.class);
        when(redis.setIfAbsent(anyString(), anyString(), eq(LEASE)))
                .thenThrow(new IllegalStateException("redis down"));

        assertNull(new ScheduledTaskLock(redis).tryAcquire("sample-task", LEASE));
    }

    /**
     * 抢到租约后必须以同一持有者标识安全释放。
     */
    @Test
    void shouldReleaseWithSameOwner() {
        RedisOperationsService redis = mock(RedisOperationsService.class);
        when(redis.setIfAbsent(eq("scheduler:lock:sample-task"), anyString(), eq(LEASE))).thenReturn(true);
        ArgumentCaptor<String> owner = ArgumentCaptor.forClass(String.class);
        ScheduledTaskLock lock = new ScheduledTaskLock(redis);

        ScheduledTaskLock.Lease lease = lock.tryAcquire("sample-task", LEASE);

        assertNotNull(lease);
        verify(redis).setIfAbsent(eq("scheduler:lock:sample-task"), owner.capture(), eq(LEASE));
        lease.close();
        // 释放时传入的必须是抢锁时的持有者标识，租约过期被他人接管后不会误删。
        verify(redis).deleteIfEquals("scheduler:lock:sample-task", owner.getValue());
    }

    /**
     * 释放失败只记录日志，不能向调度线程抛出异常。
     */
    @Test
    void shouldNotPropagateReleaseFailure() {
        RedisOperationsService redis = mock(RedisOperationsService.class);
        when(redis.setIfAbsent(anyString(), anyString(), eq(LEASE))).thenReturn(true);
        when(redis.deleteIfEquals(anyString(), anyString()))
                .thenThrow(new IllegalStateException("redis down"));
        ScheduledTaskLock.Lease lease = new ScheduledTaskLock(redis).tryAcquire("sample-task", LEASE);

        assertDoesNotThrow(lease::close);
    }

    /**
     * 任务名为空属于调用方编码错误，应立即拒绝。
     */
    @Test
    void shouldRejectBlankTaskName() {
        RedisOperationsService redis = mock(RedisOperationsService.class);

        assertThrows(IllegalArgumentException.class,
                () -> new ScheduledTaskLock(redis).tryAcquire(" ", LEASE));
    }
}
