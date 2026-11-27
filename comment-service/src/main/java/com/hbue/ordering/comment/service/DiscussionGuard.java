package com.hbue.ordering.comment.service;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.redis.DistributedLockService;
import com.hbue.ordering.common.redis.RedisOperationsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 评论提交的限流与并发保护。
 *
 * <p>两层防护：</p>
 * <ul>
 *   <li>固定窗口限流：用户维度、用户加评价维度、评价维度各一个计数器，
 *       拦截脚本刷屏与误触连发；</li>
 *   <li>分布式锁：同一用户对同一评价的提交串行化，防双击产生两条不同请求键的记录。</li>
 * </ul>
 *
 * <p>最终一致性仍由数据库唯一键 {@code (author_id, request_key)} 保证，
 * 本组件只降低无效写库与并发冲突的概率。</p>
 *
 * <p>降级方向刻意不同：限流在 Redis 故障时 fail-open（记日志后放行），因为限流只是
 * 防护措施；分布式锁无法区分"Redis 故障"与"他人正在提交"（公共组件统一返回 null），
 * 因此按 fail-closed 处理，拿不到锁就提示稍后重试，避免并发写坏数据。</p>
 *
 * @author order-system
 * @date 2026-11-25
 */
@Component
public class DiscussionGuard {

    /**
     * 当前类日志对象。
     */
    private static final Logger log = LoggerFactory.getLogger(DiscussionGuard.class);

    /**
     * 限流窗口长度：固定 60 秒，不做滑动窗口。
     *
     * <p>评论是低频写操作，固定窗口实现简单、误差可接受；滑动窗口需要额外的
     * 有序集合与清理逻辑，本期不引入。</p>
     */
    private static final Duration WINDOW = Duration.ofSeconds(60);

    /**
     * 单用户每分钟最多提交的评论数。
     */
    private static final long USER_LIMIT = 10L;

    /**
     * 单用户对同一条评价每分钟最多提交的评论数。
     */
    private static final long USER_REVIEW_LIMIT = 3L;

    /**
     * 单条评价每分钟最多接收的评论数，用于热门评价全站防洪。
     */
    private static final long REVIEW_LIMIT = 60L;

    /**
     * 提交锁的持有时长：只覆盖校验与单表写入，3 秒足够，避免锁长期占用。
     */
    private static final long LOCK_LEASE_SECONDS = 3L;

    /**
     * Redis 计数服务。
     */
    private final RedisOperationsService redis;

    /**
     * 分布式锁服务。
     */
    private final DistributedLockService locks;

    /**
     * 创建评论防护组件。
     *
     * @param redis Redis 计数服务
     * @param locks 分布式锁服务
     */
    public DiscussionGuard(RedisOperationsService redis, DistributedLockService locks) {
        this.redis = redis;
        this.locks = locks;
    }

    /**
     * 校验三个维度的限流计数。
     *
     * <p>三个计数器都执行自增，不使用短路求值，否则前一个桶超限后后面的桶不再计数，
     * 用户等待窗口结束后仍能继续刷。Redis 异常时放行并记 WARN。</p>
     *
     * @param userId   当前用户内部编号
     * @param reviewId 评价公开标识
     * @throws BusinessException 任一维度超限时抛出 429
     */
    public void checkRate(Long userId, String reviewId) {
        boolean userLimited;
        boolean pairLimited;
        boolean reviewLimited;
        try {
            userLimited = overLimit(DiscussionCacheKeys.rateUser(userId), USER_LIMIT);
            pairLimited = overLimit(DiscussionCacheKeys.rateUserReview(userId, reviewId), USER_REVIEW_LIMIT);
            reviewLimited = overLimit(DiscussionCacheKeys.rateReview(reviewId), REVIEW_LIMIT);
        } catch (RuntimeException exception) {
            // 限流只是防护措施：Redis 故障时放行，避免整个评价区不可用；唯一键仍能兜住重复提交。
            log.warn("评论限流计数失败，按放行处理，userId={}, reviewId={}", userId, reviewId, exception);
            return;
        }
        if (userLimited || pairLimited || reviewLimited) {
            throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS, "操作过于频繁，请稍后再试");
        }
    }

    /**
     * 获取同一用户同一评价的提交锁。
     *
     * <p>立即尝试，不排队等待：并发请求与其排队，不如让后到者直接重试，
     * 避免线程在锁上堆积。</p>
     *
     * @param userId   当前用户内部编号
     * @param reviewId 评价公开标识
     * @return 锁句柄，必须在 try-with-resources 中关闭
     * @throws BusinessException 未获取到锁时抛出 409
     */
    public DistributedLockService.LockHandle acquireSubmitLock(Long userId, String reviewId) {
        DistributedLockService.LockHandle handle = locks.tryAcquire(
                DiscussionCacheKeys.submitLock(userId, reviewId), 0L, LOCK_LEASE_SECONDS);
        if (handle == null) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "操作过于频繁，请稍后再试");
        }
        return handle;
    }

    /**
     * 对单个计数键自增并判断是否超限。
     *
     * <p>使用只在首次设置 TTL 的自增方法，持续请求不会刷新窗口，
     * 否则高频用户会被无限期锁在限流状态里。</p>
     *
     * @param key   计数键
     * @param limit 窗口内允许的最大次数
     * @return 超限时返回 true
     */
    private boolean overLimit(String key, long limit) {
        Long count = redis.increment(key, WINDOW);
        return count != null && count > limit;
    }
}
