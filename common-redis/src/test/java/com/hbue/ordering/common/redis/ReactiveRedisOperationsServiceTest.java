package com.hbue.ordering.common.redis;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Redis 响应式基础操作服务测试。
 *
 * @author order-system
 * @date 2026-09-25
 */
class ReactiveRedisOperationsServiceTest {

    /**
     * 验证响应式 Key 查询返回 Redis 的结果且不转换为阻塞调用。
     */
    @Test
    void hasKeyShouldReturnReactiveRedisResult() {
        ReactiveStringRedisTemplate redisTemplate =
                mock(ReactiveStringRedisTemplate.class);
        when(redisTemplate.hasKey("auth:access-token:blacklist:jti-1"))
                .thenReturn(Mono.just(true));
        ReactiveRedisOperationsService operationsService =
                new ReactiveRedisOperationsService(redisTemplate);

        Boolean result = operationsService
                .hasKey("auth:access-token:blacklist:jti-1")
                .block();

        assertTrue(result);
        verify(redisTemplate)
                .hasKey("auth:access-token:blacklist:jti-1");
    }
}
