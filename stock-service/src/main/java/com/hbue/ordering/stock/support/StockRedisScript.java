package com.hbue.ordering.stock.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 库存快照 Redis 原子操作组件。
 *
 * <p>用一条 Lua 脚本把「读取剩余量 → 判断是否足够 → 扣减」合并为原子操作，
 * 使高频下单请求先在 Redis 侧快速过滤库存不足，避免把大量必然失败的请求打到数据库。</p>
 *
 * <p>重要边界：Redis 只是可再生的加速快照，不是事实来源。快照缺失时返回
 * {@link #SNAPSHOT_MISSING}，由业务层回源数据库重建；快照与数据库的漂移由定时对账修正。
 * 即使 Redis 完全不可用，业务层也只应退化为「直接走数据库」，绝不能把 Redis 当作扣减权威。</p>
 *
 * @author order-system
 * @date 2026-10-19
 */
@Component
public class StockRedisScript {

    /**
     * 快照不存在，需要调用方回源数据库预热。
     */
    public static final int SNAPSHOT_MISSING = -1;

    /**
     * 快照中剩余数量不足以完成本次扣减。
     */
    public static final int SNAPSHOT_INSUFFICIENT = -2;

    /**
     * 原子预扣 Lua 脚本。
     *
     * <p>KEYS[1] 为库存快照 Key，ARGV[1] 为本次需要扣减的数量。</p>
     * <p>返回值约定：快照不存在返回 -1；剩余不足返回 -2；成功则返回扣减后的剩余量。</p>
     */
    private static final DefaultRedisScript<Long> PRE_DEDUCT_SCRIPT =
            new DefaultRedisScript<>(
                    "local current = redis.call('GET', KEYS[1]); "
                            + "if current == false then "
                            + "  return -1; "
                            + "end; "
                            + "local remain = tonumber(current); "
                            + "local need = tonumber(ARGV[1]); "
                            + "if remain < need then "
                            + "  return -2; "
                            + "end; "
                            + "local after = remain - need; "
                            + "redis.call('SET', KEYS[1], after); "
                            + "return after;",
                    Long.class
            );

    /**
     * 预扣补偿 Lua 脚本：把已扣减的快照数量加回。
     *
     * <p>数据库事务失败时用于撤销 Redis 侧的先行扣减，避免快照低于真实可售数量。
     * 补偿只是「加快收敛」，即使补偿失败，定时对账也会把快照拉回数据库值。</p>
     */
    private static final DefaultRedisScript<Long> COMPENSATE_SCRIPT =
            new DefaultRedisScript<>(
                    "local current = redis.call('GET', KEYS[1]); "
                            + "if current == false then "
                            + "  return -1; "
                            + "end; "
                            + "return redis.call('INCRBY', KEYS[1], ARGV[1]);",
                    Long.class
            );

    /** Spring Data Redis 字符串操作模板。 */
    private final StringRedisTemplate stringRedisTemplate;

    /** 快照 Key 前缀。 */
    private final String keyPrefix;

    /** 快照过期时间。 */
    private final Duration snapshotTtl;

    /**
     * 创建库存快照 Redis 原子操作组件。
     *
     * @param stringRedisTemplate Redis 字符串操作模板
     * @param keyPrefix 快照 Key 前缀
     * @param ttlSeconds 快照过期秒数
     */
    public StockRedisScript(
            StringRedisTemplate stringRedisTemplate,
            @Value("${stock.snapshot.key-prefix:stock:available:}")
            String keyPrefix,
            @Value("${stock.snapshot.ttl-seconds:300}") long ttlSeconds
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.keyPrefix = keyPrefix;
        this.snapshotTtl = Duration.ofSeconds(ttlSeconds);
    }

    /**
     * 用数据库可售数量重建快照。
     *
     * <p>覆盖写入并重设过期时间，供首次访问与定时对账使用。</p>
     *
     * @param windowId 档口 ID
     * @param productId 菜品 ID
     * @param availableQty 数据库中的可售数量
     */
    public void resetSnapshot(Long windowId, Long productId, Integer availableQty) {
        // 直接覆盖，保证快照等于数据库当前值。
        stringRedisTemplate.opsForValue().set(
                buildKey(windowId, productId),
                String.valueOf(availableQty),
                snapshotTtl
        );
    }

    /**
     * 读取当前快照数量，供定时对账比对使用。
     *
     * <p>快照不存在时返回 null；内容不是合法整数时同样按「不存在」处理，
     * 因为此类脏数据只可能来自异常写入，且快照本身带有过期时间会自动清除，
     * 不值得让对账任务为此中断。</p>
     *
     * @param windowId 档口 ID
     * @param productId 菜品 ID
     * @return 快照中的可售数量；不存在或内容非法时返回 null
     */
    public Integer readSnapshot(Long windowId, Long productId) {
        String value = stringRedisTemplate.opsForValue().get(buildKey(windowId, productId));
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            // 非法内容按缺失处理，后续正常下单会用数据库值重建快照。
            return null;
        }
    }

    /**
     * 原子预扣快照数量。
     *
     * @param windowId 档口 ID
     * @param productId 菜品 ID
     * @param quantity 需要扣减的数量
     * @return 扣减后的剩余量；快照不存在返回 {@link #SNAPSHOT_MISSING}；
     *         剩余不足返回 {@link #SNAPSHOT_INSUFFICIENT}
     */
    public int preDeduct(Long windowId, Long productId, Integer quantity) {
        Long result = stringRedisTemplate.execute(
                PRE_DEDUCT_SCRIPT,
                List.of(buildKey(windowId, productId)),
                String.valueOf(quantity)
        );
        if (result == null) {
            // 脚本未返回结果属于异常场景，按快照缺失处理，由调用方回源数据库兜底。
            return SNAPSHOT_MISSING;
        }
        return result.intValue();
    }

    /**
     * 补偿已经预扣的快照数量。
     *
     * @param windowId 档口 ID
     * @param productId 菜品 ID
     * @param quantity 需要加回的数量
     */
    public void compensate(Long windowId, Long productId, Integer quantity) {
        Long result = stringRedisTemplate.execute(
                COMPENSATE_SCRIPT,
                List.of(buildKey(windowId, productId)),
                String.valueOf(quantity)
        );
        if (result == null) {
            // 补偿失败不抛出：快照是派生数据，定时对账会把漂移修正回数据库值。
            return;
        }
    }

    /**
     * 拼装库存快照 Key。
     *
     * @param windowId 档口 ID
     * @param productId 菜品 ID
     * @return 形如 stock:available:{windowId}:{productId} 的 Key
     */
    public String buildKey(Long windowId, Long productId) {
        return keyPrefix + windowId + ":" + productId;
    }
}
