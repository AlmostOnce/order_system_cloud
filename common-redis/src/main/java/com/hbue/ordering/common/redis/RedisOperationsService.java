package com.hbue.ordering.common.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * Redis 常用基础操作组件。
 *
 * <p>统一封装字符串、Hash、过期时间、Key 判断和删除操作，
 * 供各业务模块按需注入使用。</p>
 *
 * @author order-system
 * @date 2026-09-25
 */
@Component
public class RedisOperationsService {

    /**
     * Spring Data Redis 字符串操作模板。
     */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 创建 Redis 基础操作组件。
     *
     * @param stringRedisTemplate Redis 字符串操作模板
     */
    public RedisOperationsService(
            StringRedisTemplate stringRedisTemplate
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 保存字符串值。
     *
     * @param key Redis Key
     * @param value 字符串值
     */
    public void setValue(String key, String value) {
        // 使用 Redis Value 操作保存字符串。
        stringRedisTemplate.opsForValue().set(key, value);
    }

    /**
     * 保存带过期时间的字符串值。
     *
     * @param key Redis Key
     * @param value 字符串值
     * @param timeToLive 有效时长
     */
    public void setValue(
            String key,
            String value,
            Duration timeToLive
    ) {
        // 使用 Redis Value 操作保存字符串并设置 TTL。
        stringRedisTemplate.opsForValue().set(
                key,
                value,
                timeToLive
        );
    }

    /**
     * 根据 Key 查询字符串值。
     *
     * @param key Redis Key
     * @return 字符串值；Key 不存在时返回 null
     */
    public String getValue(String key) {
        // 返回 Redis Value 操作的查询结果。
        return stringRedisTemplate.opsForValue().get(key);
    }

    /**
     * 保存 Hash 中的多个字段和值。
     *
     * @param key Redis Key
     * @param values Hash 字段和值
     */
    public void putHashValues(
            String key,
            Map<String, String> values
    ) {
        // 将传入的字段和值写入 Redis Hash。
        stringRedisTemplate.opsForHash().putAll(key, values);
    }

    /**
     * 查询 Hash 中的全部字段和值。
     *
     * @param key Redis Key
     * @return Hash 字段和值；Hash 不存在时返回空 Map
     */
    public Map<Object, Object> getHashValues(String key) {
        // 返回 Redis Hash 中保存的全部字段和值。
        return stringRedisTemplate.opsForHash().entries(key);
    }

    /**
     * 设置 Redis Key 的过期时间。
     *
     * @param key Redis Key
     * @param timeToLive 有效时长
     * @return Redis 是否确认设置成功；无法确认时可能返回 null
     */
    public Boolean expire(String key, Duration timeToLive) {
        // 返回 Redis 对过期时间设置操作的结果。
        return stringRedisTemplate.expire(key, timeToLive);
    }

    /**
     * 判断 Redis Key 是否存在。
     *
     * @param key Redis Key
     * @return Key 是否存在；无法确认时可能返回 null
     */
    public Boolean hasKey(String key) {
        // 返回 Redis 对 Key 存在性查询的结果。
        return stringRedisTemplate.hasKey(key);
    }

    /**
     * 删除 Redis Key。
     *
     * @param key Redis Key
     * @return Redis 是否确认删除；Key 不存在或无法确认时可能返回 false 或 null
     */
    public Boolean delete(String key) {
        // 返回 Redis 对 Key 删除操作的结果。
        return stringRedisTemplate.delete(key);
    }
}
