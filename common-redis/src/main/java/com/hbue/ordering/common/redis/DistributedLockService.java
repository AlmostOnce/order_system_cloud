package com.hbue.ordering.common.redis;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Redis 分布式锁组件。
 *
 * <p>基于 Redisson 的 {@link RLock} 提供通用互斥能力，
 * 用于热点数据缓存重建等需要避免并发重复加载的场景。</p>
 *
 * <p>获取锁失败不抛出异常，由调用方决定降级策略：
 * 缓存重建场景通常降级为直接查询数据库，优先保证业务可用。</p>
 *
 * @author order-system
 * @date 2026-10-14
 */
public class DistributedLockService {

    /**
     * 默认等待锁的最长时间，单位为秒。
     */
    private static final long DEFAULT_WAIT_TIME_SECONDS = 3L;

    /**
     * 默认持有锁的最长时间，单位为秒。
     */
    private static final long DEFAULT_LEASE_TIME_SECONDS = 10L;

    /**
     * 日志对象。
     */
    private static final Logger log =
            LoggerFactory.getLogger(DistributedLockService.class);

    /**
     * Redisson 客户端。
     */
    private final RedissonClient redissonClient;

    /**
     * 创建分布式锁组件。
     *
     * @param redissonClient Redisson 客户端
     */
    public DistributedLockService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 使用默认等待时间和持锁时间尝试获取分布式锁。
     *
     * @param lockKey 锁 Key
     * @return 锁句柄；未获取到锁时返回 null
     */
    public LockHandle tryAcquire(String lockKey) {
        return tryAcquire(
                lockKey,
                DEFAULT_WAIT_TIME_SECONDS,
                DEFAULT_LEASE_TIME_SECONDS
        );
    }

    /**
     * 尝试获取分布式锁。
     *
     * <p>等待超时、线程被中断或 Redis 故障时均返回 null，
     * 不向调用方抛出异常。</p>
     *
     * @param lockKey 锁 Key
     * @param waitSeconds 等待锁的最长时间，单位为秒
     * @param leaseSeconds 持有锁的最长时间，单位为秒
     * @return 锁句柄；未获取到锁时返回 null
     */
    public LockHandle tryAcquire(
            String lockKey,
            long waitSeconds,
            long leaseSeconds
    ) {
        // 空锁 Key 无法定位资源，属于调用方编码错误。
        if (lockKey == null || lockKey.isBlank()) {
            throw new IllegalArgumentException("分布式锁 Key 不能为空");
        }

        RLock distributedLock = redissonClient.getLock(lockKey);
        boolean acquired;
        try {
            // 使用固定租约，避免解锁失败后看门狗无限续期造成资源长期阻塞。
            acquired = distributedLock.tryLock(
                    waitSeconds,
                    leaseSeconds,
                    TimeUnit.SECONDS
            );
        } catch (InterruptedException exception) {
            // 恢复线程中断标记，避免吞掉容器或服务器的中断信号。
            Thread.currentThread().interrupt();
            log.warn("等待分布式锁时线程被中断，锁 Key={}", lockKey);
            return null;
        } catch (RuntimeException exception) {
            // Redis 或 Redisson 故障时降级为未获取到锁，由调用方决定后续策略。
            log.error("获取分布式锁失败，锁 Key=" + lockKey, exception);
            return null;
        }

        // 等待超时表示其他实例正在处理同一资源。
        if (!acquired) {
            return null;
        }

        // 将锁交给调用方，并由 try-with-resources 负责释放。
        return new LockHandle(distributedLock);
    }

    /**
     * 已获取分布式锁的释放句柄。
     */
    public static class LockHandle implements AutoCloseable {

        /**
         * 日志对象。
         */
        private static final Logger log =
                LoggerFactory.getLogger(LockHandle.class);

        /**
         * 当前线程获取的 Redisson 锁。
         */
        private final RLock distributedLock;

        /**
         * 创建分布式锁释放句柄。
         *
         * @param distributedLock 已获取的 Redisson 锁
         */
        private LockHandle(RLock distributedLock) {
            this.distributedLock = distributedLock;
        }

        /**
         * 在当前线程仍持有锁时释放分布式锁。
         */
        @Override
        public void close() {
            try {
                // 防止锁已过期或已不属于当前线程时错误执行 unlock。
                if (distributedLock.isHeldByCurrentThread()) {
                    distributedLock.unlock();
                }
            } catch (RuntimeException exception) {
                // 释放失败由固定租约兜底回收，不影响已完成的业务处理结果。
                log.error("释放分布式锁失败", exception);
            }
        }
    }
}
