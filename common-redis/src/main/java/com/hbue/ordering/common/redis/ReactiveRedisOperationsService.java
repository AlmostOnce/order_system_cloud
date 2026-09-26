package com.hbue.ordering.common.redis;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Redis 响应式基础操作组件。
 *
 * <p>为 WebFlux 服务提供非阻塞 Redis 基础操作。</p>
 *
 * @author order-system
 * @date 2026-09-25
 */
@Component
public class ReactiveRedisOperationsService {

    /**
     * Redis 响应式字符串操作模板。
     */
    private final ReactiveStringRedisTemplate reactiveStringRedisTemplate;

    /**
     * 创建 Redis 响应式基础操作组件。
     *
     * @param reactiveStringRedisTemplate Redis 响应式字符串操作模板
     */
    public ReactiveRedisOperationsService(
            ReactiveStringRedisTemplate reactiveStringRedisTemplate
    ) {
        this.reactiveStringRedisTemplate = reactiveStringRedisTemplate;
    }

    /**
     * 非阻塞地判断 Redis Key 是否存在。
     *
     * @param key Redis Key
     * @return Redis Key 是否存在
     */
    public Mono<Boolean> hasKey(String key) {
        // 将 Redis 的 Key 存在性查询作为响应式结果返回。
        return reactiveStringRedisTemplate.hasKey(key);
    }
}
