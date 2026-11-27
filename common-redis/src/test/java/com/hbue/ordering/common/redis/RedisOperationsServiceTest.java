package com.hbue.ordering.common.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

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

    /**
     * 有序集合操作测试替身，用于验证 ZADD/ZCARD/ZRANGE/ZREM 是否被正确委托。
     */
    private org.springframework.data.redis.core.ZSetOperations<String, String> zSetOperations;

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
        zSetOperations = mock(org.springframework.data.redis.core.ZSetOperations.class);

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
     * 带 TTL 的原子自增应通过 Redis 脚本完成并返回新计数。
     */
    @Test
    @SuppressWarnings("unchecked")
    void incrementWithExpirationShouldReturnAtomicCounterValue() {
        when(stringRedisTemplate.<Long>execute(
                any(RedisScript.class),
                eq(List.of("sample:counter")),
                eq("900000")
        )).thenReturn(2L);

        // 以原子方式增加计数，并在计数 Key 首次创建时设置 15 分钟过期时间。
        Long count = redisOperationsService.increment(
                "sample:counter",
                Duration.ofMinutes(15)
        );

        // 确认 Redis 返回递增后的计数，供业务层判断是否达到限制阈值。
        assertEquals(2L, count);
        verify(stringRedisTemplate).execute(
                any(RedisScript.class),
                eq(List.of("sample:counter")),
                eq("900000")
        );
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

    /**
     * 有序集合写入应调用 ZADD 并返回确认结果。
     */
    @Test
    void addToSortedSetShouldDelegateToRedis() {
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.add("sample:zset", "member", 1000D)).thenReturn(true);

        // 写入成员并按分数排序。
        Boolean result = redisOperationsService.addToSortedSet("sample:zset", "member", 1000D);

        // 确认使用 Redis 有序集合写入。
        assertTrue(result);
        verify(zSetOperations).add("sample:zset", "member", 1000D);
    }

    /**
     * 有序集合数量统计应调用 ZCARD。
     */
    @Test
    void sortedSetSizeShouldReturnCardinality() {
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.zCard("sample:zset")).thenReturn(3L);

        // 统计有序集合成员数量。
        Long size = redisOperationsService.sortedSetSize("sample:zset");

        // 确认返回 Redis 统计结果。
        assertEquals(3L, size);
    }

    /**
     * 有序集合区间读取应按分数升序返回成员，Redis 返回 null 时按空集合处理。
     */
    @Test
    void sortedSetRangeShouldReturnOrderedMembers() {
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.range("sample:zset", 0L, 1L))
                .thenReturn(new java.util.LinkedHashSet<>(List.of("first", "second")));

        // 读取最旧的 2 个成员。
        List<String> members = redisOperationsService.sortedSetRange("sample:zset", 0L, 1L);

        // 确认保持 Redis 返回的分数升序。
        assertEquals(List.of("first", "second"), members);
    }

    /**
     * 有序集合删除应调用 ZREM 并返回删除数量。
     */
    @Test
    void removeFromSortedSetShouldReturnRemovedCount() {
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.remove("sample:zset", "member")).thenReturn(1L);

        // 删除指定成员。
        Long removed = redisOperationsService.removeFromSortedSet("sample:zset", "member");

        // 确认 Redis 返回删除数量。
        assertEquals(1L, removed);
    }
}
