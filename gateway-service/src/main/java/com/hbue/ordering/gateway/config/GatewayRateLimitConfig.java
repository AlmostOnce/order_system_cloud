package com.hbue.ordering.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gateway Redis 限流配置。
 *
 * <p>为 Gateway 登录接口创建 Redis 令牌桶限流器，
 * 限流状态由 Redis 保存，因此多个 Gateway 实例共享同一限流额度。</p>
 *
 * @author order-system
 * @date 2026-09-25
 */
@Configuration
public class GatewayRateLimitConfig {

    /**
     * 创建登录接口使用的 Redis 令牌桶限流器。
     *
     * @param replenishRate 每秒补充的令牌数
     * @param burstCapacity 令牌桶最大容量
     * @param requestedTokens 每个请求消耗的令牌数
     * @return Redis 令牌桶限流器
     */
    @Bean
    public RedisRateLimiter loginRedisRateLimiter(
            @Value("${gateway.rate-limit.login.replenish-rate:1}")
            int replenishRate,
            @Value("${gateway.rate-limit.login.burst-capacity:50}")
            int burstCapacity,
            @Value("${gateway.rate-limit.login.requested-tokens:10}")
            int requestedTokens
    ) {
        // 拒绝非法参数，避免应用以不可预期的限流规则启动。
        if (replenishRate < 1
                || burstCapacity < 1
                || requestedTokens < 1
                || requestedTokens > burstCapacity) {
            throw new IllegalArgumentException(
                    "Gateway 登录限流参数必须大于零，且 requested-tokens "
                            + "不能大于 burst-capacity"
            );
        }

        // 以令牌桶方式初始化限流器，具体数值由 application.yml 配置。
        return new RedisRateLimiter(
                replenishRate,
                burstCapacity,
                requestedTokens
        );
    }
}
