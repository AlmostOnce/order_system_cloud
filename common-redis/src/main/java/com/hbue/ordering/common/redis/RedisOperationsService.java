package com.hbue.ordering.common.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Redis 常用基础操作组件。
 *
 * <p>统一封装字符串、Hash、过期时间、Key 判断和删除操作，
 * 供各业务模块按需注入使用。</p>
 *
 * <p>本组件基于同步 {@link StringRedisTemplate}，方法调用会等待 Redis 操作结果，
 * 应在同步业务线程中使用；不要在 Gateway/WebFlux 事件循环中调用。
 * Redis 访问异常按 Spring Data Redis 的异常语义向调用方传播。</p>
 *
 * @author order-system
 * @date 2026-09-25
 */
@Component
public class RedisOperationsService {

    /**
     * 原子增加计数并在首次创建时设置过期时间的 Lua 脚本。
     *
     * <p>将 INCR 与 PEXPIRE 放在同一条 Redis 脚本中执行，避免进程在两条命令之间
     * 异常退出时留下永不过期的计数 Key。</p>
     */
    private static final DefaultRedisScript<Long> INCREMENT_WITH_TTL_SCRIPT =
            new DefaultRedisScript<>(
                    "local count = redis.call('INCR', KEYS[1]); "
                            + "if count == 1 then "
                            + "redis.call('PEXPIRE', KEYS[1], ARGV[1]); "
                            + "end; "
                            + "return count;",
                    Long.class
            );

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
     * 原子读取并删除字符串值。
     *
     * <p>底层使用 Redis 的 GETDEL 命令，「取值」和「删除」不可分割。用于
     * 一次性凭据（如图片验证码）：并发请求同时到达时只有一个能拿到原值，
     * 其余请求拿到 null，避免同一凭据被重复使用。</p>
     *
     * @param key Redis Key
     * @return Key 对应的字符串值；Key 不存在时返回 null
     */
    public String getAndDelete(String key) {
        // 返回 GETDEL 的读取结果，Key 已被同一次操作删除。
        return stringRedisTemplate.opsForValue().getAndDelete(key);
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
     * 执行调用方提供的 Redis 脚本，按原样传递 Key 和参数。
     *
     * @param script 已配置结果类型的脚本
     * @param keys 脚本使用的 Redis Key
     * @param args 脚本参数
     * @return Redis 返回的脚本结果，可能为 null
     */
    public <T> T executeScript(RedisScript<T> script, List<String> keys, Object... args) {
        return stringRedisTemplate.execute(script, keys, args);
    }

    /**
     * 对 Key 保存的整数执行原子自增。
     *
     * <p>用于失败次数等计数场景；Key 不存在时按 0 起算。
     * 本方法不设置过期时间，由调用方在返回值为 1（即首次计数）时
     * 自行调用 {@link #expire(String, Duration)} 补设 TTL，
     * 避免每加一次都重置过期时间导致计数永不到期。</p>
     *
     * @param key Redis Key
     * @return 自增后的值
     */
    public Long increment(String key) {
        // 返回 Redis 对整数自增操作的结果。
        return stringRedisTemplate.opsForValue().increment(key);
    }

    /**
     * 原子增加计数，并仅在计数 Key 首次创建时设置过期时间。
     *
     * <p>适用于登录失败次数等固定时间窗口计数。后续自增不会刷新 TTL，避免持续失败
     * 不断延长计数窗口；自增和首次设置 TTL 由一段 Lua 脚本原子完成。</p>
     *
     * @param key Redis 计数 Key
     * @param timeToLive 首次计数后的有效时长，至少为 1 毫秒
     * @return 自增后的计数；Redis 未返回结果时为 null
     * @throws IllegalArgumentException Key 为空或有效时长不足 1 毫秒时抛出
     */
    public Long increment(String key, Duration timeToLive) {
        // 参数不合法时立即失败，防止 Redis 收到无效 Key 或无法生效的 TTL。
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Redis Key 不能为空");
        }
        if (timeToLive == null
                || timeToLive.isNegative()
                || timeToLive.isZero()
                || timeToLive.toMillis() < 1L) {
            throw new IllegalArgumentException(
                    "Redis Key 的有效时长必须至少为 1 毫秒"
            );
        }

        // Lua 保证 INCR 和首次 PEXPIRE 不会被其它请求或进程中断拆开。
        return stringRedisTemplate.execute(
                INCREMENT_WITH_TTL_SCRIPT,
                List.of(key),
                String.valueOf(timeToLive.toMillis())
        );
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
