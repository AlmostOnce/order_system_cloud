package com.hbue.ordering.search.support;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

/**
 * 协调增量写入与索引切换，使用 Redisson 看门狗避免长任务锁提前到期。
 *
 * @author order-system
 * @date 2026-10-28
 */
@Component
public class SearchIndexLock {
    private static final String LOCK_KEY = "search:menu:index-switch";
    private final RedissonClient redisson;

    public SearchIndexLock(RedissonClient redisson) { this.redisson = redisson; }

    public <T> T withReadLock(Supplier<T> action) {
        return withLock(redisson.getReadWriteLock(LOCK_KEY).readLock(), action);
    }

    public <T> T withWriteLock(Supplier<T> action) {
        return withLock(redisson.getReadWriteLock(LOCK_KEY).writeLock(), action);
    }

    public <T> T withWindowLock(Long windowId, Supplier<T> action) {
        return withLock(redisson.getLock("search:menu:reindex:" + windowId), action);
    }

    private <T> T withLock(RLock lock, Supplier<T> action) {
        try {
            if (!lock.tryLock(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("搜索索引正在切换，请稍后重试");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待搜索索引锁时被中断", exception);
        }
        try {
            return action.get();
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }
}
