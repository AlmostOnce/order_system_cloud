package com.hbue.ordering.product.service;

import com.hbue.ordering.product.dto.request.ProductCategorySaveRequest;
import com.hbue.ordering.product.dto.request.ProductSaveRequest;
import com.hbue.ordering.product.dto.request.WindowSaveRequest;
import com.hbue.ordering.product.vo.ProductAdminVO;
import com.hbue.ordering.product.vo.ProductCategoryAdminVO;
import com.hbue.ordering.product.vo.WindowAdminVO;

import java.util.List;

/**
 * 窗口、分类和菜品管理服务。
 *
 * @author order-system
 * @date 2026-09-28
 */
public interface ProductAdministrationService {

    /**
     * 查询所有窗口，包括已停用窗口。
     *
     * @return 窗口管理列表
     */
    List<WindowAdminVO> listWindows();

    /**
     * 创建窗口。
     *
     * @param request 窗口字段
     * @return 新建窗口
     */
    WindowAdminVO createWindow(WindowSaveRequest request);

    /**
     * 全量更新窗口。
     *
     * @param windowId 窗口 ID
     * @param request 窗口字段
     * @return 更新后的窗口
     */
    WindowAdminVO updateWindow(Long windowId, WindowSaveRequest request);

    /**
     * 停用窗口；不删除窗口及其历史目录数据。
     *
     * @param windowId 窗口 ID
     */
    void disableWindow(Long windowId);

    /**
     * 查询指定窗口的所有分类，包括已停用分类。
     *
     * @param windowId 窗口 ID
     * @return 分类管理列表
     */
    List<ProductCategoryAdminVO> listCategories(Long windowId);

    /**
     * 创建窗口分类。
     *
     * @param windowId 窗口 ID
     * @param request 分类字段
     * @return 新建分类
     */
    ProductCategoryAdminVO createCategory(
            Long windowId,
            ProductCategorySaveRequest request
    );

    /**
     * 全量更新分类。
     *
     * @param categoryId 分类 ID
     * @param request 分类字段
     * @return 更新后的分类
     */
    ProductCategoryAdminVO updateCategory(
            Long categoryId,
            ProductCategorySaveRequest request
    );

    /**
     * 停用分类；该分类下的菜品保留。
     *
     * @param categoryId 分类 ID
     */
    void disableCategory(Long categoryId);

    /**
     * 查询指定窗口的所有菜品，包括已下架菜品。
     *
     * @param windowId 窗口 ID
     * @return 菜品管理列表
     */
    List<ProductAdminVO> listProducts(Long windowId);

    /**
     * 查询单个菜品。
     *
     * @param productId 菜品 ID
     * @return 菜品详情
     */
    ProductAdminVO getProduct(Long productId);

    /**
     * 在指定窗口创建菜品。
     *
     * @param windowId 窗口 ID
     * @param request 菜品字段
     * @return 新建菜品
     */
    ProductAdminVO createProduct(Long windowId, ProductSaveRequest request);

    /**
     * 全量更新菜品。
     *
     * @param productId 菜品 ID
     * @param request 菜品字段
     * @return 更新后的菜品
     */
    ProductAdminVO updateProduct(Long productId, ProductSaveRequest request);

    /**
     * 下架菜品；不物理删除菜品记录。
     *
     * @param productId 菜品 ID
     */
    void disableProduct(Long productId);
}
