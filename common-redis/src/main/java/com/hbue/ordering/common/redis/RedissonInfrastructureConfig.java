package com.hbue.ordering.common.redis;

import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 基础设施装配。
 *
 * <p>仅在运行期存在 Redisson 客户端时注册分布式锁与布隆过滤器组件，
 * 供引入 Redisson 的业务服务按需注入使用。</p>
 *
 * <p>未引入 Redisson 的服务（例如使用 Lettuce 响应式 Redis 的 Gateway）
 * 不会加载本配置，避免 Redisson 自动装配与既有 Redis 客户端冲突。</p>
 *
 * @author order-system
 * @date 2026-10-14
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(RedissonClient.class)
public class RedissonInfrastructureConfig {

    /**
     * 注册分布式锁组件。
     *
     * @param redissonClient Redisson 客户端
     * @return 分布式锁组件
     */
    @Bean
    public DistributedLockService distributedLockService(
            RedissonClient redissonClient
    ) {
        return new DistributedLockService(redissonClient);
    }

    /**
     * 注册布隆过滤器组件。
     *
     * @param redissonClient Redisson 客户端
     * @return 布隆过滤器组件
     */
    @Bean
    public RedisBloomFilterService redisBloomFilterService(
            RedissonClient redissonClient
    ) {
        return new RedisBloomFilterService(redissonClient);
    }
}
