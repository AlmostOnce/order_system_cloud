package com.hbue.ordering.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.redis.DistributedLockService;
import com.hbue.ordering.product.cache.ProductBloomFilterManager;
import com.hbue.ordering.product.cache.ProductCacheKeys;
import com.hbue.ordering.product.cache.ProductMenuCacheService;
import com.hbue.ordering.product.mapper.DiningWindowMapper;
import com.hbue.ordering.product.mapper.ProductCategoryMapper;
import com.hbue.ordering.product.mapper.ProductMapper;
import com.hbue.ordering.product.mapper.RestaurantMapper;
import com.hbue.ordering.product.model.DiningWindowDO;
import com.hbue.ordering.product.model.ProductCategoryDO;
import com.hbue.ordering.product.model.ProductDO;
import com.hbue.ordering.product.model.RestaurantDO;
import com.hbue.ordering.product.service.ProductMenuService;
import com.hbue.ordering.product.vo.MenuProductVO;
import com.hbue.ordering.product.vo.ProductCategoryMenuVO;
import com.hbue.ordering.product.vo.RestaurantSummaryVO;
import com.hbue.ordering.product.vo.WindowMenuVO;
import com.hbue.ordering.product.vo.WindowSummaryVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 从商品目录组装顾客可见的窗口菜单，并协调缓存、布隆过滤器与回源查询。
 *
 * <p>本类只读取商品目录并构造展示视图，不负责管理端写操作。窗口菜单查询先用布隆过滤器
 * 和空值缓存挡住无效 ID，再使用 Redis 缓存；缓存击穿时用按窗口区分的分布式锁合并重建，
 * 最终菜单内容仍以商品数据库中的启停、分类和售卖状态为准。</p>
 *
 * @author order-system
 * @date 2026-09-28
 */
@Service
public class ProductMenuServiceImpl implements ProductMenuService {

    /** 启用状态值。 */
    private static final int ACTIVE_STATUS = 1;

    /** 在售状态值。 */
    private static final int ON_SALE_STATUS = 1;

    /** 餐厅未配置展示排序或所属餐厅已停用时使用的兜底排序值，排在已配置餐厅之后。 */
    private static final int UNRANKED_SORT_ORDER = Integer.MAX_VALUE;

    /** 窗口数据 Mapper。 */
    private final DiningWindowMapper diningWindowMapper;

    /** 餐厅数据 Mapper。 */
    private final RestaurantMapper restaurantMapper;

    /** 分类数据 Mapper。 */
    private final ProductCategoryMapper productCategoryMapper;

    /** 菜品数据 Mapper。 */
    private final ProductMapper productMapper;

    /** 商品菜单缓存读写组件。 */
    private final ProductMenuCacheService menuCacheService;

    /** 商品布隆过滤器管理器。 */
    private final ProductBloomFilterManager bloomFilterManager;

    /** 分布式锁组件。 */
    private final DistributedLockService distributedLockService;

    /**
     * 创建窗口菜单查询服务。
     *
     * @param diningWindowMapper 窗口数据 Mapper
     * @param restaurantMapper 餐厅数据 Mapper
     * @param productCategoryMapper 分类数据 Mapper
     * @param productMapper 菜品数据 Mapper
     * @param menuCacheService 商品菜单缓存读写组件
     * @param bloomFilterManager 商品布隆过滤器管理器
     * @param distributedLockService 分布式锁组件
     */
    public ProductMenuServiceImpl(
            DiningWindowMapper diningWindowMapper,
            RestaurantMapper restaurantMapper,
            ProductCategoryMapper productCategoryMapper,
            ProductMapper productMapper,
            ProductMenuCacheService menuCacheService,
            ProductBloomFilterManager bloomFilterManager,
            DistributedLockService distributedLockService
    ) {
        this.diningWindowMapper = diningWindowMapper;
        this.restaurantMapper = restaurantMapper;
        this.productCategoryMapper = productCategoryMapper;
        this.productMapper = productMapper;
        this.menuCacheService = menuCacheService;
        this.bloomFilterManager = bloomFilterManager;
        this.distributedLockService = distributedLockService;
    }

    /**
     * 查询所有启用餐厅，按展示排序值升序排列。
     *
     * <p>排序值小者在前，相同则按餐厅 ID 升序，与数据库查询顺序保持一致。
     * 餐厅数量有限且展示排序允许随时调整，此处直接回源数据库、不做缓存，
     * 避免排序改动被缓存延后生效。</p>
     *
     * @return 启用餐厅摘要列表
     */
    @Override
    public List<RestaurantSummaryVO> listActiveRestaurants() {
        List<RestaurantDO> restaurants = restaurantMapper.selectList(
                new QueryWrapper<RestaurantDO>()
                        .eq("status", ACTIVE_STATUS)
                        .orderByAsc("sort_order")
                        .orderByAsc("restaurant_id")
        );

        List<RestaurantSummaryVO> summaries = new ArrayList<>();
        for (RestaurantDO restaurant : restaurants) {
            summaries.add(new RestaurantSummaryVO(
                    restaurant.getRestaurantId(),
                    restaurant.getRestaurantCode(),
                    restaurant.getRestaurantName(),
                    restaurant.getLocation()
            ));
        }
        return summaries;
    }

    /**
     * 查询所有启用窗口，先按所属餐厅的展示排序、再按窗口 ID 稳定排序。
     *
     * <p>优先读取缓存；未命中时回源数据库组装列表并写回缓存。
     * 窗口列表重建代价低，此路径不加分布式锁。</p>
     *
     * @return 启用窗口摘要列表
     */
    @Override
    public List<WindowSummaryVO> listActiveWindows() {
        List<WindowSummaryVO> cached = menuCacheService.findActiveWindows();
        if (cached != null) {
            return cached;
        }

        List<DiningWindowDO> windows = diningWindowMapper.selectList(
                new QueryWrapper<DiningWindowDO>()
                        .eq("status", ACTIVE_STATUS)
                        .orderByAsc("window_id")
        );

        // 先取启用餐厅的展示排序，让同一餐厅的档口在列表中相邻出现。
        Map<Long, Integer> restaurantSortOrders = loadRestaurantSortOrders();

        List<DiningWindowDO> orderedWindows = new ArrayList<>(windows);
        orderedWindows.sort(new Comparator<DiningWindowDO>() {
            @Override
            public int compare(DiningWindowDO left, DiningWindowDO right) {
                int leftOrder = sortOrderOf(restaurantSortOrders, left.getRestaurantId());
                int rightOrder = sortOrderOf(restaurantSortOrders, right.getRestaurantId());
                if (leftOrder != rightOrder) {
                    return Integer.compare(leftOrder, rightOrder);
                }
                // 食堂排序相同时仍按食堂分组，避免不同食堂的窗口交错。
                long leftRestaurantId = left.getRestaurantId() == null
                        ? Long.MAX_VALUE : left.getRestaurantId();
                long rightRestaurantId = right.getRestaurantId() == null
                        ? Long.MAX_VALUE : right.getRestaurantId();
                int restaurantComparison = Long.compare(leftRestaurantId, rightRestaurantId);
                if (restaurantComparison != 0) {
                    return restaurantComparison;
                }
                int windowOrder = Integer.compare(
                        left.getSortOrder() == null ? 0 : left.getSortOrder(),
                        right.getSortOrder() == null ? 0 : right.getSortOrder()
                );
                if (windowOrder != 0) {
                    return windowOrder;
                }
                return Long.compare(left.getWindowId(), right.getWindowId());
            }
        });

        List<WindowSummaryVO> summaries = new ArrayList<>();
        for (DiningWindowDO window : orderedWindows) {
            summaries.add(new WindowSummaryVO(
                    window.getWindowId(),
                    window.getRestaurantId(),
                    window.getWindowCode(),
                    window.getWindowName(),
                    window.getDescription(),
                    window.getSortOrder(),
                    window.getLocation(),
                    window.getImageUrl()
            ));
        }
        menuCacheService.saveActiveWindows(summaries);
        return summaries;
    }

    /**
     * 查询启用窗口下的启用分类和在售菜品。
     *
     * <p>查询顺序：校验窗口 ID、布隆过滤器拦截、空值标记拦截、读缓存、
     * 抢互斥锁后双重检查缓存并回源数据库。布隆过滤器和空值标记用于
     * 防止无效 ID 反复穿透，分布式锁用于避免热点窗口在缓存失效瞬间
     * 被大量并发请求同时重建（缓存击穿）。</p>
     *
     * @param windowId 窗口 ID
     * @return 窗口菜单
     */
    @Override
    public WindowMenuVO getWindowMenu(Long windowId) {
        validateWindowId(windowId);

        // 布隆过滤器判定一定不存在时直接拒绝，不再查询缓存和数据库。
        if (!bloomFilterManager.mightContainWindow(windowId)) {
            throw new BusinessException(
                    CommonErrorCode.NOT_FOUND,
                    "窗口不存在或已停用"
            );
        }

        // 命中空值标记说明此前已确认窗口无效，直接拒绝，不再查询数据库。
        if (menuCacheService.isWindowMenuMarkedAbsent(windowId)) {
            throw new BusinessException(
                    CommonErrorCode.NOT_FOUND,
                    "窗口不存在或已停用"
            );
        }

        WindowMenuVO cached = menuCacheService.findWindowMenu(windowId);
        if (cached != null) {
            return cached;
        }

        // 抢互斥锁，只让一个实例回源数据库重建缓存。
        try (DistributedLockService.LockHandle lock =
                     distributedLockService.tryAcquire(
                             ProductCacheKeys.windowMenuLock(windowId)
                     )) {
            if (lock == null) {
                // 未抢到锁时降级直接查库返回，不写缓存，避免与持锁方竞争写入。
                // 此分支由当前请求直接回源但不写缓存，避免未持锁线程与锁内重建并发写入。
                WindowMenuVO menu = loadWindowMenu(windowId);
                if (menu == null) {
                    throw new BusinessException(
                            CommonErrorCode.NOT_FOUND,
                            "窗口不存在或已停用"
                    );
                }
                return menu;
            }

            // 双重检查：持锁期间其他实例可能已把缓存填好。
            WindowMenuVO lockedCached = menuCacheService.findWindowMenu(windowId);
            if (lockedCached != null) {
                return lockedCached;
            }

            WindowMenuVO menu = loadWindowMenu(windowId);
            if (menu == null) {
                // 记录空值标记，后续无效请求直接命中标记而不再查库。
                menuCacheService.saveWindowMenuAbsent(windowId);
                throw new BusinessException(
                        CommonErrorCode.NOT_FOUND,
                        "窗口不存在或已停用"
                );
            }
            menuCacheService.saveWindowMenu(windowId, menu);
            return menu;
        }
    }

    /**
     * 回源数据库组装窗口菜单。
     *
     * @param windowId 窗口 ID
     * @return 窗口菜单；窗口不存在或已停用时返回 null
     */
    private WindowMenuVO loadWindowMenu(Long windowId) {
        DiningWindowDO window = diningWindowMapper.selectById(windowId);
        if (window == null
                || !Integer.valueOf(ACTIVE_STATUS).equals(window.getStatus())) {
            return null;
        }

        List<ProductCategoryDO> categories = productCategoryMapper.selectList(
                new QueryWrapper<ProductCategoryDO>()
                        .eq("window_id", windowId)
                        .eq("status", ACTIVE_STATUS)
                        .orderByAsc("sort_order")
                        .orderByAsc("category_id")
        );

        List<ProductDO> products = productMapper.selectList(
                new QueryWrapper<ProductDO>()
                        .eq("window_id", windowId)
                        .eq("sale_status", ON_SALE_STATUS)
                        .orderByAsc("category_id")
                        .orderByAsc("sort_order")
                        .orderByAsc("product_id")
        );

        return assembleMenu(window, categories, products);
    }

    /**
     * 组装分类与菜品；被停用分类下的菜品不会出现在顾客菜单中。
     *
     * @param window 启用窗口
     * @param categories 启用分类
     * @param products 当前在售菜品
     * @return 窗口菜单视图
     */
    private WindowMenuVO assembleMenu(
            DiningWindowDO window,
            List<ProductCategoryDO> categories,
            List<ProductDO> products
    ) {
        Map<Long, List<MenuProductVO>> productsByCategory = new HashMap<>();
        for (ProductCategoryDO category : categories) {
            productsByCategory.put(category.getCategoryId(), new ArrayList<>());
        }

        List<MenuProductVO> uncategorizedProducts = new ArrayList<>();
        for (ProductDO product : products) {
            MenuProductVO menuProduct = toMenuProduct(product);
            if (product.getCategoryId() == null) {
                uncategorizedProducts.add(menuProduct);
                continue;
            }

            List<MenuProductVO> categoryProducts =
                    productsByCategory.get(product.getCategoryId());
            if (categoryProducts != null) {
                categoryProducts.add(menuProduct);
            }
        }

        List<ProductCategoryMenuVO> categoryMenus = new ArrayList<>();
        for (ProductCategoryDO category : categories) {
            categoryMenus.add(new ProductCategoryMenuVO(
                    category.getCategoryId(),
                    category.getCategoryName(),
                    category.getSortOrder(),
                    productsByCategory.get(category.getCategoryId())
            ));
        }

        return new WindowMenuVO(
                window.getWindowId(),
                window.getWindowCode(),
                window.getWindowName(),
                window.getDescription(),
                window.getLocation(),
                categoryMenus,
                uncategorizedProducts,
                window.getImageUrl()
        );
    }

    /**
     * 将菜品持久化对象转换为菜单项。
     *
     * @param product 菜品持久化对象
     * @return 菜单菜品
     */
    private MenuProductVO toMenuProduct(ProductDO product) {
        return new MenuProductVO(
                product.getProductId(),
                product.getProductCode(),
                product.getProductName(),
                product.getDescription(),
                product.getImageUrl(),
                product.getPrice(),
                product.getSortOrder()
        );
    }

    /**
     * 读取启用餐厅的展示排序，构造「餐厅 ID → 排序值」映射。
     *
     * @return 餐厅 ID 到展示排序值的映射
     */
    private Map<Long, Integer> loadRestaurantSortOrders() {
        List<RestaurantDO> restaurants = restaurantMapper.selectList(
                new QueryWrapper<RestaurantDO>()
                        .eq("status", ACTIVE_STATUS)
        );

        Map<Long, Integer> sortOrders = new HashMap<>();
        for (RestaurantDO restaurant : restaurants) {
            sortOrders.put(restaurant.getRestaurantId(), restaurant.getSortOrder());
        }
        return sortOrders;
    }

    /**
     * 取餐厅的展示排序值。
     *
     * <p>档口未挂餐厅、所属餐厅已停用或餐厅未配置排序值时，一律取兜底值排到最后，
     * 保证排序结果稳定且不会因空值抛出异常。</p>
     *
     * @param sortOrders 餐厅 ID 到展示排序值的映射
     * @param restaurantId 餐厅 ID
     * @return 展示排序值
     */
    private int sortOrderOf(Map<Long, Integer> sortOrders, Long restaurantId) {
        if (restaurantId == null) {
            return UNRANKED_SORT_ORDER;
        }
        Integer sortOrder = sortOrders.get(restaurantId);
        if (sortOrder == null) {
            return UNRANKED_SORT_ORDER;
        }
        return sortOrder;
    }

    /**
     * 校验窗口 ID。
     *
     * @param windowId 窗口 ID
     */
    private void validateWindowId(Long windowId) {
        if (windowId == null || windowId <= 0) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "窗口 ID 必须为正整数"
            );
        }
    }
}
