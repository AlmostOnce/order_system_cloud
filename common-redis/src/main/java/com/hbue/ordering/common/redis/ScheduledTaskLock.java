package com.hbue.ordering.common.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * 定时任务分布式互斥租约。
 *
 * <p>多实例部署时 Spring 的 {@code @Scheduled} 会在每个实例上各自触发，推荐目录对账、
 * 热榜刷新、优惠券到期提醒这类"全局只应执行一次"的任务会被重复执行，放大数据库与远程调用。
 * 本组件基于 Redis 的 SET NX EX 提供最小互斥：同一任务名同一时间只有一个实例拿到租约，
 * 其余实例跳过本轮，由下一轮调度自然重试。</p>
 *
 * <p>设计边界：</p>
 * <ul>
 *   <li>租约为固定时长，不会自动续期。租约必须大于一次任务执行的最长耗时，否则任务超时后
 *       其它实例可能并行执行；本组件不提供 fencing token，任务自身仍需保证幂等。</li>
 *   <li>Redis 故障或抢锁异常时 {@link #tryAcquire} 返回 null，任务跳过本轮而不是退化为
 *       多实例并行，避免故障期间重复执行放大下游压力。</li>
 *   <li>释放使用"值匹配才删除"，避免本次任务超时后误删其它实例已重新获取的租约。</li>
 * </ul>
 *
 * @author order-system
 * @date 2026-11-27
 */
@Component
public class ScheduledTaskLock {

    /**
     * 当前类日志对象。
     */
    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskLock.class);

    /**
     * 定时任务锁 Key 前缀，与业务缓存、令牌等命名空间隔离。
     */
    private static final String KEY_PREFIX = "scheduler:lock:";

    /**
     * Redis 基础操作组件。
     */
    private final RedisOperationsService redisOperationsService;

    /**
     * 创建定时任务互斥租约组件。
     *
     * @param redisOperationsService Redis 基础操作组件
     */
    public ScheduledTaskLock(RedisOperationsService redisOperationsService) {
        this.redisOperationsService = redisOperationsService;
    }

    /**
     * 尝试获取任务租约。
     *
     * @param taskName 稳定的任务名，同一任务在所有实例上必须一致
     * @param lease    租约时长，必须大于一次执行的最长耗时
     * @return 租约句柄，应在 try-with-resources 中使用；未抢到或 Redis 不可用时返回 null
     * @throws IllegalArgumentException 任务名为空或租约时长非法时抛出
     */
    public Lease tryAcquire(String taskName, Duration lease) {
        if (taskName == null || taskName.isBlank()) {
            throw new IllegalArgumentException("定时任务名称不能为空");
        }
        // 每次尝试使用唯一持有者标识，释放时据此判断租约是否仍属于本次执行。
        String owner = UUID.randomUUID().toString();
        String key = KEY_PREFIX + taskName;
        Boolean acquired;
        try {
            acquired = redisOperationsService.setIfAbsent(key, owner, lease);
        } catch (RuntimeException exception) {
            // Redis 不可用时跳过本轮：宁可少跑一次，也不在多实例上同时重复执行。
            log.warn("获取定时任务租约失败，本轮跳过，task={}", taskName, exception);
            return null;
        }
        if (!Boolean.TRUE.equals(acquired)) {
            // 另一个实例持锁中，或 Redis 无法确认结果，两种情况都执行跳过。
            return null;
        }
        return new Lease(taskName, key, owner);
    }

    /**
     * 已获取的定时任务租约。
     *
     * <p>关闭时先比较持有者标识再删除；租约已过期并被其它实例接管时不会误删。</p>
     */
    public final class Lease implements AutoCloseable {

        /**
         * 任务名，仅用于日志定位。
         */
        private final String taskName;

        /**
         * 锁 Key。
         */
        private final String key;

        /**
         * 本次执行的唯一持有者标识。
         */
        private final String owner;

        /**
         * 创建租约句柄。
         *
         * @param taskName 任务名
         * @param key      锁 Key
         * @param owner    持有者标识
         */
        private Lease(String taskName, String key, String owner) {
            this.taskName = taskName;
            this.key = key;
            this.owner = owner;
        }

        /**
         * 释放租约。
         *
         * <p>释放失败不向调用方抛出：租约本身会到期自动回收，任务结果不受影响。</p>
         */
        @Override
        public void close() {
            try {
                redisOperationsService.deleteIfEquals(key, owner);
            } catch (RuntimeException exception) {
                log.warn("释放定时任务租约失败，等待租约自动过期，task={}", taskName, exception);
            }
        }
    }
}
