package com.hbue.ordering.common.redis;

import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Redis 布隆过滤器组件。
 *
 * <p>基于 Redisson 的 {@link RBloomFilter} 提供共享布隆过滤器能力：
 * 位图保存在 Redis 中，多个服务实例共享同一份数据，
 * 用于提前拦掉一定不存在的 ID，减轻缓存穿透压力。</p>
 *
 * <p>布隆过滤器只负责判定"一定不存在"：
 * 判定为不存在时可以直接拒绝请求；判定为可能存在时仍需继续查询缓存和数据库，
 * 不能把布隆过滤器当作数据来源。</p>
 *
 * <p>布隆过滤器不支持删除元素，已写入的 ID 会一直参与判定，
 * 因此删除数据后仍需依赖空值缓存兜底。</p>
 *
 * @author order-system
 * @date 2026-10-14
 */
public class RedisBloomFilterService {

    /**
     * 日志对象。
     */
    private static final Logger log =
            LoggerFactory.getLogger(RedisBloomFilterService.class);

    /**
     * Redisson 客户端。
     */
    private final RedissonClient redissonClient;

    /**
     * 创建布隆过滤器组件。
     *
     * @param redissonClient Redisson 客户端
     */
    public RedisBloomFilterService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 幂等初始化布隆过滤器。
     *
     * <p>过滤器已存在时直接复用，不覆盖已有位图，
     * 避免多个实例启动时相互覆盖造成已写入数据丢失。</p>
     *
     * @param filterName 过滤器名称
     * @param expectedInsertions 预期元素数量
     * @param falseProbability 预期误判率
     * @return 可用的布隆过滤器
     */
    public RBloomFilter<Long> getOrInitFilter(
            String filterName,
            long expectedInsertions,
            double falseProbability
    ) {
        // 空名称无法定位过滤器，属于调用方编码错误。
        if (filterName == null || filterName.isBlank()) {
            throw new IllegalArgumentException("布隆过滤器名称不能为空");
        }

        RBloomFilter<Long> filter = redissonClient.getBloomFilter(filterName);
        if (filter.isExists()) {
            return filter;
        }

        boolean initialized = filter.tryInit(
                expectedInsertions,
                falseProbability
        );
        if (!initialized) {
            // 并发启动时其他实例可能已完成初始化，此时复用即可。
            log.warn("布隆过滤器 {} 已被其他实例初始化，直接复用", filterName);
        }
        return filter;
    }

    /**
     * 判断 ID 是否可能存在于布隆过滤器。
     *
     * <p>过滤器未初始化或 Redis 故障时返回 true（放行），
     * 保证故障场景下不会误拦真实存在的数据，最多退化为一次数据库查询。</p>
     *
     * @param filterName 过滤器名称
     * @param id 数据 ID
     * @return true 表示"可能存在"，false 表示"一定不存在"
     */
    public boolean mightContain(String filterName, long id) {
        try {
            RBloomFilter<Long> filter =
                    redissonClient.getBloomFilter(filterName);
            if (!filter.isExists()) {
                return true;
            }
            return filter.contains(id);
        } catch (RuntimeException exception) {
            log.error("查询布隆过滤器失败，过滤器名称=" + filterName, exception);
            return true;
        }
    }

    /**
     * 将新增数据的 ID 写入布隆过滤器。
     *
     * @param filterName 过滤器名称
     * @param id 数据 ID
     * @return true 表示写入成功；过滤器未初始化或 Redis 故障时返回 false
     */
    public boolean add(String filterName, long id) {
        try {
            RBloomFilter<Long> filter =
                    redissonClient.getBloomFilter(filterName);
            if (!filter.isExists()) {
                log.warn("布隆过滤器 {} 尚未初始化，跳过写入", filterName);
                return false;
            }
            return filter.add(id);
        } catch (RuntimeException exception) {
            log.error("写入布隆过滤器失败，过滤器名称=" + filterName, exception);
            return false;
        }
    }
}
