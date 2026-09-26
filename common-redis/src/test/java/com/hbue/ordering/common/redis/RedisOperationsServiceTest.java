package com.hbue.ordering.common.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Redis 基础操作服务测试。
 *
 * @author order-system
 * @date 2026-09-25
 */
class RedisOperationsServiceTest {

    private StringRedisTemplate stringRedisTemplate;

    private ValueOperations<String, String> valueOperations;

    private HashOperations<String, Object, Object> hashOperations;

    private RedisOperationsService redisOperationsService;

    /**
     * 初始化 Redis 模板及其基础操作对象。
     */
    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        stringRedisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        hashOperations = mock(HashOperations.class);

        when(stringRedisTemplate.opsForValue())
                .thenReturn(valueOperations);
        when(stringRedisTemplate.opsForHash())
                .thenReturn(hashOperations);

        redisOperationsService =
                new RedisOperationsService(stringRedisTemplate);
    }

    /**
     * 无过期时间的字符串写入应调用 Redis Value 操作。
     */
    @Test
    void setValueShouldWriteStringWithoutExpiration() {
        // 执行字符串写入。
        redisOperationsService.setValue("sample:key", "sample-value");

        // 确认使用 Redis 的字符串写入操作。
        verify(valueOperations).set("sample:key", "sample-value");
    }

    /**
     * 带过期时间的字符串写入应保留指定 TTL。
     */
    @Test
    void setValueShouldWriteStringWithExpiration() {
        Duration timeToLive = Duration.ofMinutes(5);

        // 执行带 TTL 的字符串写入。
        redisOperationsService.setValue(
                "sample:key",
                "sample-value",
                timeToLive
        );

        // 确认写入时传入了原始 TTL。
        verify(valueOperations).set(
                "sample:key",
                "sample-value",
                timeToLive
        );
    }

    /**
     * 字符串读取应返回 Redis 中对应的值。
     */
    @Test
    void getValueShouldReturnStoredString() {
        when(valueOperations.get("sample:key"))
                .thenReturn("sample-value");

        // 读取 Redis 字符串。
        String value = redisOperationsService.getValue("sample:key");

        // 确认返回 Redis 查询结果。
        assertEquals("sample-value", value);
    }

    /**
     * Hash 写入应保存传入的全部字段和值。
     */
    @Test
    void putHashValuesShouldWriteAllEntries() {
        Map<String, String> entries = new HashMap<>();
        entries.put("userId", "42");
        entries.put("roleCode", "CUSTOMER");

        // 写入 Hash 字段和值。
        redisOperationsService.putHashValues("sample:hash", entries);

        // 确认写入完整的 Hash 数据。
        verify(hashOperations).putAll("sample:hash", entries);
    }

    /**
     * Hash 查询应返回 Redis 中保存的全部字段和值。
     */
    @Test
    void getHashValuesShouldReturnStoredEntries() {
        Map<Object, Object> entries = new HashMap<>();
        entries.put("userId", "42");
        when(hashOperations.entries("sample:hash")).thenReturn(entries);

        // 查询 Hash 的全部字段和值。
        Map<Object, Object> result =
                redisOperationsService.getHashValues("sample:hash");

        // 确认返回 Redis 查询到的 Hash 数据。
        assertSame(entries, result);
    }

    /**
     * 过期时间设置应返回 Redis 操作结果。
     */
    @Test
    void expireShouldReturnRedisResult() {
        Duration timeToLive = Duration.ofDays(1);
        when(stringRedisTemplate.expire("sample:key", timeToLive))
                .thenReturn(true);

        // 为 Redis Key 设置过期时间。
        Boolean result = redisOperationsService.expire(
                "sample:key",
                timeToLive
        );

        // 确认 Redis 设置过期时间成功。
        assertTrue(result);
    }

    /**
     * Key 存在判断应返回 Redis 查询结果。
     */
    @Test
    void hasKeyShouldReturnRedisResult() {
        when(stringRedisTemplate.hasKey("sample:key")).thenReturn(true);

        // 查询 Redis Key 是否存在。
        Boolean result = redisOperationsService.hasKey("sample:key");

        // 确认 Redis 返回 Key 存在。
        assertTrue(result);
    }

    /**
     * Key 删除应返回 Redis 操作结果。
     */
    @Test
    void deleteShouldReturnRedisResult() {
        when(stringRedisTemplate.delete("sample:key")).thenReturn(true);

        // 删除 Redis Key。
        Boolean result = redisOperationsService.delete("sample:key");

        // 确认 Redis 返回删除成功。
        assertTrue(result);
    }
}
