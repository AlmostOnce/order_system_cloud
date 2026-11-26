package com.hbue.ordering.comment.service;

/**
 * 评价评论相关 Redis Key 的集中命名。
 *
 * <p>所有 Key 由服务端按"用户编号 + 目标"拼装，绝不使用客户端传入的片段，
 * 避免越权读取他人的准入结果或污染缓存。Key 前缀统一为 {@code review:discussion:}，
 * 便于按前缀排查与清理。</p>
 *
 * @author order-system
 * @date 2026-11-25
 */
public final class DiscussionCacheKeys {

    /**
     * 消费准入结果缓存前缀。
     */
    private static final String ELIGIBLE_PREFIX = "review:discussion:eligible:";

    /**
     * 单用户单评价的提交限流前缀。
     */
    private static final String RATE_USER_REVIEW_PREFIX = "review:discussion:rate:user-review:";

    /**
     * 单用户全局限流前缀。
     */
    private static final String RATE_USER_PREFIX = "review:discussion:rate:user:";

    /**
     * 单条评价的全站限流前缀。
     */
    private static final String RATE_REVIEW_PREFIX = "review:discussion:rate:review:";

    /**
     * 提交串行化锁前缀。
     */
    private static final String SUBMIT_LOCK_PREFIX = "review:discussion:submit:";

    /**
     * 工具类不允许实例化。
     */
    private DiscussionCacheKeys() {
    }

    /**
     * 消费准入结果缓存键。
     *
     * @param userId     用户内部编号
     * @param targetType PRODUCT 或 WINDOW
     * @param targetId   菜品或档口编号
     * @return Redis Key
     */
    public static String eligible(Long userId, String targetType, Long targetId) {
        return ELIGIBLE_PREFIX + userId + ":" + targetType + ":" + targetId;
    }

    /**
     * 单用户单评价的限流计数键。
     *
     * @param userId   用户内部编号
     * @param reviewId 评价公开标识
     * @return Redis Key
     */
    public static String rateUserReview(Long userId, String reviewId) {
        return RATE_USER_REVIEW_PREFIX + userId + ":" + reviewId;
    }

    /**
     * 单用户全局限流计数键。
     *
     * @param userId 用户内部编号
     * @return Redis Key
     */
    public static String rateUser(Long userId) {
        return RATE_USER_PREFIX + userId;
    }

    /**
     * 单条评价的全站限流计数键。
     *
     * @param reviewId 评价公开标识
     * @return Redis Key
     */
    public static String rateReview(String reviewId) {
        return RATE_REVIEW_PREFIX + reviewId;
    }

    /**
     * 提交串行化锁键。
     *
     * <p>锁键不包含请求键：双击产生的是两个不同请求键，唯一键无法拦截，
     * 只能靠"同一用户同一评价"维度的锁串行化。</p>
     *
     * @param userId   用户内部编号
     * @param reviewId 评价公开标识
     * @return Redis Key
     */
    public static String submitLock(Long userId, String reviewId) {
        return SUBMIT_LOCK_PREFIX + userId + ":" + reviewId;
    }
}
