package com.hbue.ordering.common.redis;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Redis 响应式基础操作组件。
 *
 * <p>使用 {@link ReactiveStringRedisTemplate} 返回 Reactor 类型，Redis I/O 在订阅后
 * 以响应式信号交付，不在调用处同步等待结果；适用于 Gateway 等 WebFlux 请求链。
 * 不要在该组件中调用 {@code block()}，也不要将同步 Redis 操作混入事件循环线程。</p>
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
     * @return 发出 Key 是否存在的结果；Redis 访问失败时以错误信号结束
     */
    public Mono<Boolean> hasKey(String key) {
        // 将 Redis 的 Key 存在性查询作为响应式结果返回。
        return reactiveStringRedisTemplate.hasKey(key);
    }
}
