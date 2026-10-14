package com.hbue.ordering.product.cache;

import com.hbue.ordering.common.redis.RedisBloomFilterService;
import org.springframework.stereotype.Component;

/**
 * 商品布隆过滤器管理器。
 *
 * <p>包装底层 {@link RedisBloomFilterService}，为窗口和菜品两类数据提供
 * 统一的"可能存在/一定不存在"判定入口，用于在查询缓存和数据库前
 * 提前拦截一定不存在的 ID，减轻缓存穿透压力。</p>
 *
 * <p>参数为 null 时统一返回 true（视作可能存在并放行），
 * 保证非法参数仍由后续业务校验处理，不因布隆过滤器误判而改变语义。</p>
 *
 * @author order-system
 * @date 2026-10-14
 */
@Component
public class ProductBloomFilterManager {

    /** 窗口过滤器预期元素数量。 */
    private static final long WINDOW_EXPECTED_INSERTIONS = 1000L;

    /** 菜品过滤器预期元素数量。 */
    private static final long PRODUCT_EXPECTED_INSERTIONS = 10000L;

    /** 布隆过滤器预期误判率。 */
    private static final double FALSE_PROBABILITY = 0.01D;

    /** 布隆过滤器底层组件。 */
    private final RedisBloomFilterService redisBloomFilterService;

    /**
     * 创建商品布隆过滤器管理器。
     *
     * @param redisBloomFilterService 布隆过滤器底层组件
     */
    public ProductBloomFilterManager(
            RedisBloomFilterService redisBloomFilterService
    ) {
        this.redisBloomFilterService = redisBloomFilterService;
    }

    /**
     * 幂等初始化窗口与菜品布隆过滤器。
     *
     * <p>过滤器已存在时复用已有位图，可安全在服务启动时重复调用。</p>
     */
    public void initFilters() {
        redisBloomFilterService.getOrInitFilter(
                ProductCacheKeys.WINDOW_BLOOM_FILTER,
                WINDOW_EXPECTED_INSERTIONS,
                FALSE_PROBABILITY
        );
        redisBloomFilterService.getOrInitFilter(
                ProductCacheKeys.PRODUCT_BLOOM_FILTER,
                PRODUCT_EXPECTED_INSERTIONS,
                FALSE_PROBABILITY
        );
    }

    /**
     * 判断窗口 ID 是否可能存在于窗口布隆过滤器。
     *
     * @param windowId 窗口 ID
     * @return true 表示可能存在；false 表示一定不存在
     */
    public boolean mightContainWindow(Long windowId) {
        // 空参数交由业务校验处理，此处按可能存在放行。
        if (windowId == null) {
            return true;
        }
        return redisBloomFilterService.mightContain(
                ProductCacheKeys.WINDOW_BLOOM_FILTER,
                windowId
        );
    }

    /**
     * 判断菜品 ID 是否可能存在于菜品布隆过滤器。
     *
     * @param productId 菜品 ID
     * @return true 表示可能存在；false 表示一定不存在
     */
    public boolean mightContainProduct(Long productId) {
        // 空参数交由业务校验处理，此处按可能存在放行。
        if (productId == null) {
            return true;
        }
        return redisBloomFilterService.mightContain(
                ProductCacheKeys.PRODUCT_BLOOM_FILTER,
                productId
        );
    }

    /**
     * 将窗口 ID 写入窗口布隆过滤器。
     *
     * @param windowId 窗口 ID
     */
    public void addWindow(Long windowId) {
        if (windowId == null) {
            return;
        }
        redisBloomFilterService.add(
                ProductCacheKeys.WINDOW_BLOOM_FILTER,
                windowId
        );
    }

    /**
     * 将菜品 ID 写入菜品布隆过滤器。
     *
     * @param productId 菜品 ID
     */
    public void addProduct(Long productId) {
        if (productId == null) {
            return;
        }
        redisBloomFilterService.add(
                ProductCacheKeys.PRODUCT_BLOOM_FILTER,
                productId
        );
    }
}
