package com.hbue.ordering.comment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.comment.mapper.CommentMapper;
import com.hbue.ordering.comment.service.impl.RatingSummaryServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 评分读缓存失效测试：新增/修改/删除评价后必须能删到读取时使用的同一个键。
 */
class RatingSummaryServiceTest {

    private final CommentMapper rows = mock(CommentMapper.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final RatingSummaryServiceImpl service = new RatingSummaryServiceImpl(rows, redis, new ObjectMapper());

    /**
     * 失效必须删除与 {@code get} 完全一致的键，不能因前缀不一致而删空。
     */
    @Test
    void evictDeletesTheExactProductRatingKey() {
        service.evict(9L);

        verify(redis).delete("comment:rating:v1:9");
        verifyNoInteractions(rows);
    }

    /**
     * Redis 故障时删除缓存不能把已提交的评价写入变成失败，异常必须被吞掉并由 TTL 兜底。
     */
    @Test
    void redisFailureDuringEvictDoesNotPropagate() {
        when(redis.delete(anyString())).thenThrow(new IllegalStateException("redis down"));

        assertDoesNotThrow(() -> service.evict(9L));
    }
}
