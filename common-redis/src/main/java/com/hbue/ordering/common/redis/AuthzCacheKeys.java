package com.hbue.ordering.common.redis;

import java.time.Duration;

/**
 * 授权相关 Redis 缓存键常量。
 *
 * <p>数据范围不进 JWT，各业务服务查询 user-service 后把结果缓存到 Redis，
 * 键名集中在这里定义，避免各服务硬编码字符串造成不一致。</p>
 *
 * @author order-system
 * @date 2026-10-13
 */
public final class AuthzCacheKeys {

    /** 用户数据范围缓存键前缀。 */
    private static final String USER_SCOPE_PREFIX = "authz:user-scope:";

    /** 用户数据范围缓存有效期，撤销范围后由 user-service 主动删除。 */
    public static final Duration USER_SCOPE_TTL = Duration.ofSeconds(300);

    /**
     * 禁止实例化常量类。
     */
    private AuthzCacheKeys() {
    }

    /**
     * 生成用户数据范围缓存键。
     *
     * @param userId 用户 ID
     * @return Redis 缓存键
     */
    public static String userScope(Long userId) {
        return USER_SCOPE_PREFIX + userId;
    }
}
