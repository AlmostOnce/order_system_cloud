package com.hbue.ordering.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.product.mapper.DiningWindowMapper;
import com.hbue.ordering.product.mapper.ProductCategoryMapper;
import com.hbue.ordering.product.mapper.ProductMapper;
import com.hbue.ordering.product.model.DiningWindowDO;
import com.hbue.ordering.product.model.ProductCategoryDO;
import com.hbue.ordering.product.model.ProductDO;
import com.hbue.ordering.product.service.ProductMenuService;
import com.hbue.ordering.product.vo.MenuProductVO;
import com.hbue.ordering.product.vo.ProductCategoryMenuVO;
import com.hbue.ordering.product.vo.WindowMenuVO;
import com.hbue.ordering.product.vo.WindowSummaryVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 从商品目录组装顾客可见的窗口菜单。
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

    /** 窗口数据 Mapper。 */
    private final DiningWindowMapper diningWindowMapper;

    /** 分类数据 Mapper。 */
    private final ProductCategoryMapper productCategoryMapper;

    /** 菜品数据 Mapper。 */
    private final ProductMapper productMapper;

    /**
     * 创建窗口菜单查询服务。
     *
     * @param diningWindowMapper 窗口数据 Mapper
     * @param productCategoryMapper 分类数据 Mapper
     * @param productMapper 菜品数据 Mapper
     */
    public ProductMenuServiceImpl(
            DiningWindowMapper diningWindowMapper,
            ProductCategoryMapper productCategoryMapper,
            ProductMapper productMapper
    ) {
        this.diningWindowMapper = diningWindowMapper;
        this.productCategoryMapper = productCategoryMapper;
        this.productMapper = productMapper;
    }

    /**
     * 查询所有启用窗口，并按 ID 稳定排序。
     *
     * @return 启用窗口摘要列表
     */
    @Override
    public List<WindowSummaryVO> listActiveWindows() {
        List<DiningWindowDO> windows = diningWindowMapper.selectList(
                new QueryWrapper<DiningWindowDO>()
                        .eq("status", ACTIVE_STATUS)
                        .orderByAsc("window_id")
        );

        List<WindowSummaryVO> summaries = new ArrayList<>();
        for (DiningWindowDO window : windows) {
            summaries.add(new WindowSummaryVO(
                    window.getWindowId(),
                    window.getWindowCode(),
                    window.getWindowName(),
                    window.getLocation()
            ));
        }
        return summaries;
    }

    /**
     * 查询启用窗口下的启用分类和在售菜品。
     *
     * @param windowId 窗口 ID
     * @return 窗口菜单
     */
    @Override
    public WindowMenuVO getWindowMenu(Long windowId) {
        validateWindowId(windowId);

        DiningWindowDO window = diningWindowMapper.selectById(windowId);
        if (window == null
                || !Integer.valueOf(ACTIVE_STATUS).equals(window.getStatus())) {
            throw new BusinessException(
                    CommonErrorCode.NOT_FOUND,
                    "窗口不存在或已停用"
            );
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
                window.getLocation(),
                categoryMenus,
                uncategorizedProducts
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
