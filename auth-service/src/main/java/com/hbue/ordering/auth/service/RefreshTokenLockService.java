package com.hbue.ordering.auth.service;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

/**
 * Refresh Token 轮换分布式锁服务。
 *
 * <p>同一枚 Refresh Token 使用相同的摘要锁 Key，
 * 不同 Token 使用不同锁，避免重复轮换同时允许不同用户并行刷新。</p>
 *
 * @author order-system
 * @date 2026-09-25
 */
@Component
public class RefreshTokenLockService {

    /**
     * 分布式锁 Key 前缀。
     */
    private static final String LOCK_KEY_PREFIX =
            "auth:refresh-token:lock:";

    /**
     * 等待分布式锁的最长时间，单位为秒。
     */
    private static final long LOCK_WAIT_TIME_SECONDS = 5L;

    /**
     * 分布式锁的最长持有时间，单位为秒。
     */
    private static final long LOCK_LEASE_TIME_SECONDS = 30L;

    /**
     * 日志对象。
     */
    private static final Logger log =
            LoggerFactory.getLogger(RefreshTokenLockService.class);

    /**
     * Redisson 客户端。
     */
    private final RedissonClient redissonClient;

    /**
     * 创建 Refresh Token 分布式锁服务。
     *
     * @param redissonClient Redisson 客户端
     */
    public RefreshTokenLockService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 获取指定 Refresh Token 对应的分布式锁。
     *
     * @param refreshToken 原始 Refresh Token
     * @return 用于在 try-with-resources 中释放锁的句柄
     */
    public LockHandle acquire(String refreshToken) {
        // 空 Token 无法生成有效锁 Key，按无效凭据处理。
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(
                    CommonErrorCode.UNAUTHORIZED,
                    "Refresh Token 无效或已过期"
            );
        }

        RLock distributedLock;
        boolean acquired;
        try {
            // 锁 Key 只包含 Token 摘要，不暴露客户端持有的原始 Token。
            distributedLock = redissonClient.getLock(
                    buildLockKey(refreshToken)
            );

            // 使用有界租约，避免释放失败后看门狗无限续期造成该 Token 永久阻塞。
            acquired = distributedLock.tryLock(
                    LOCK_WAIT_TIME_SECONDS,
                    LOCK_LEASE_TIME_SECONDS,
                    TimeUnit.SECONDS
            );
        } catch (InterruptedException exception) {
            // 恢复线程中断标记，避免吞掉容器或服务器的中断信号。
            Thread.currentThread().interrupt();
            throw new BusinessException(
                    CommonErrorCode.SERVICE_UNAVAILABLE,
                    "刷新请求等待分布式锁时被中断"
            );
        } catch (RuntimeException exception) {
            // Redis 或 Redisson 故障时按 fail-closed 处理，不继续轮换 Token。
            log.error("获取 Refresh Token 分布式锁失败", exception);
            throw new BusinessException(
                    CommonErrorCode.SERVICE_UNAVAILABLE,
                    "Refresh Token 锁服务暂时不可用"
            );
        }

        // 等待超时表示该 Token 正在被刷新，不允许并发请求越过锁继续执行。
        if (!acquired) {
            throw new BusinessException(
                    CommonErrorCode.SERVICE_UNAVAILABLE,
                    "Refresh Token 正在处理中，请稍后重试"
            );
        }

        // 将锁交给调用方，并由 try-with-resources 负责释放。
        return new LockHandle(distributedLock);
    }

    /**
     * 根据 Refresh Token 生成不包含原文的锁 Key。
     *
     * @param refreshToken 原始 Refresh Token
     * @return 使用 SHA-256 摘要构造的锁 Key
     */
    private String buildLockKey(String refreshToken) {
        try {
            // 使用 JDK SHA-256 生成固定长度的 Token 摘要。
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(
                    refreshToken.getBytes(StandardCharsets.UTF_8)
            );

            // 转为小写十六进制字符串后拼接业务前缀。
            return LOCK_KEY_PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            // SHA-256 属于 JDK 必需算法，缺失时视为运行环境配置错误。
            throw new IllegalStateException(
                    "当前 Java 环境不支持 SHA-256",
                    exception
            );
        }
    }

    /**
     * 已获取分布式锁的释放句柄。
     */
    public static class LockHandle implements AutoCloseable {

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
                // 释放失败由固定租约兜底回收，不能覆盖已完成的刷新结果。
                log.error("释放 Refresh Token 分布式锁失败", exception);
            }
        }
    }
}
