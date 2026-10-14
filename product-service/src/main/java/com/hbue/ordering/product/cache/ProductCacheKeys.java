package com.hbue.ordering.product.cache;

/**
 * 商品菜单缓存键与布隆过滤器名称常量。
 *
 * <p>集中定义缓存 Key 前缀和布隆过滤器名称，避免各业务类硬编码字符串
 * 造成键名不一致，方便统一维护与排查。</p>
 *
 * @author order-system
 * @date 2026-10-14
 */
public final class ProductCacheKeys {

    /** 启用窗口列表缓存键。 */
    public static final String ACTIVE_WINDOWS = "product:windows:active";

    /** 窗口布隆过滤器名称。 */
    public static final String WINDOW_BLOOM_FILTER = "product:bloom:window";

    /** 菜品布隆过滤器名称。 */
    public static final String PRODUCT_BLOOM_FILTER = "product:bloom:product";

    /** 窗口菜单缓存键前缀。 */
    private static final String WINDOW_MENU_PREFIX = "product:menu:window:";

    /** 窗口菜单空值标记缓存键前缀。 */
    private static final String WINDOW_MENU_NULL_PREFIX = "product:null:menu:window:";

    /** 窗口菜单重建分布式锁 Key 前缀。 */
    private static final String WINDOW_MENU_LOCK_PREFIX = "product:lock:menu:window:";

    /**
     * 禁止实例化常量类。
     */
    private ProductCacheKeys() {
    }

    /**
     * 生成窗口菜单缓存键。
     *
     * @param windowId 窗口 ID
     * @return Redis 缓存键
     */
    public static String windowMenu(Long windowId) {
        return WINDOW_MENU_PREFIX + windowId;
    }

    /**
     * 生成窗口菜单空值标记缓存键。
     *
     * <p>用于缓存"窗口不存在或已停用"的结果，防止同一无效 ID 反复穿透到数据库。</p>
     *
     * @param windowId 窗口 ID
     * @return Redis 缓存键
     */
    public static String windowMenuNull(Long windowId) {
        return WINDOW_MENU_NULL_PREFIX + windowId;
    }

    /**
     * 生成窗口菜单重建分布式锁 Key。
     *
     * @param windowId 窗口 ID
     * @return Redis 锁 Key
     */
    public static String windowMenuLock(Long windowId) {
        return WINDOW_MENU_LOCK_PREFIX + windowId;
    }
}
