package com.hbue.ordering.product.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.product.dto.request.ProductCategorySaveRequest;
import com.hbue.ordering.product.dto.request.ProductSaveRequest;
import com.hbue.ordering.product.dto.request.WindowSaveRequest;
import com.hbue.ordering.product.service.ProductAdministrationService;
import com.hbue.ordering.product.vo.ProductAdminVO;
import com.hbue.ordering.product.vo.ProductCategoryAdminVO;
import com.hbue.ordering.product.vo.WindowAdminVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 窗口、分类和菜品管理接口。
 *
 * <p>所有接口只允许 ADMIN 角色访问。</p>
 *
 * @author order-system
 * @date 2026-09-28
 */
@Validated
@RestController
@RequestMapping("/products/admin")
@PreAuthorize("hasRole('ADMIN')")
public class ProductAdministrationController {

    /** 目录管理服务。 */
    private final ProductAdministrationService productAdministrationService;

    /**
     * 创建目录管理 Controller。
     *
     * @param productAdministrationService 目录管理服务
     */
    public ProductAdministrationController(
            ProductAdministrationService productAdministrationService
    ) {
        this.productAdministrationService = productAdministrationService;
    }

    /**
     * 查询所有窗口，包括已停用窗口。
     *
     * @return 窗口列表
     */
    @GetMapping("/windows")
    public ApiResponse<List<WindowAdminVO>> listWindows() {
        return ApiResponse.success(productAdministrationService.listWindows());
    }

    /**
     * 创建窗口。
     *
     * @param request 窗口字段
     * @return 新建窗口
     */
    @PostMapping("/windows")
    public ApiResponse<WindowAdminVO> createWindow(
            @Valid @RequestBody WindowSaveRequest request
    ) {
        return ApiResponse.success(
                productAdministrationService.createWindow(request)
        );
    }

    /**
     * 全量更新窗口。
     *
     * @param windowId 窗口 ID
     * @param request 窗口字段
     * @return 更新后的窗口
     */
    @PutMapping("/windows/{windowId}")
    public ApiResponse<WindowAdminVO> updateWindow(
            @PathVariable("windowId") @Positive Long windowId,
            @Valid @RequestBody WindowSaveRequest request
    ) {
        return ApiResponse.success(
                productAdministrationService.updateWindow(windowId, request)
        );
    }

    /**
     * 停用窗口。
     *
     * @param windowId 窗口 ID
     * @return 空成功响应
     */
    @DeleteMapping("/windows/{windowId}")
    public ApiResponse<Void> disableWindow(
            @PathVariable("windowId") @Positive Long windowId
    ) {
        productAdministrationService.disableWindow(windowId);
        return ApiResponse.success(null);
    }

    /**
     * 查询窗口下所有分类，包括已停用分类。
     *
     * @param windowId 窗口 ID
     * @return 分类列表
     */
    @GetMapping("/windows/{windowId}/categories")
    public ApiResponse<List<ProductCategoryAdminVO>> listCategories(
            @PathVariable("windowId") @Positive Long windowId
    ) {
        return ApiResponse.success(
                productAdministrationService.listCategories(windowId)
        );
    }

    /**
     * 在窗口下创建分类。
     *
     * @param windowId 窗口 ID
     * @param request 分类字段
     * @return 新建分类
     */
    @PostMapping("/windows/{windowId}/categories")
    public ApiResponse<ProductCategoryAdminVO> createCategory(
            @PathVariable("windowId") @Positive Long windowId,
            @Valid @RequestBody ProductCategorySaveRequest request
    ) {
        return ApiResponse.success(
                productAdministrationService.createCategory(windowId, request)
        );
    }

    /**
     * 全量更新分类。
     *
     * @param categoryId 分类 ID
     * @param request 分类字段
     * @return 更新后的分类
     */
    @PutMapping("/categories/{categoryId}")
    public ApiResponse<ProductCategoryAdminVO> updateCategory(
            @PathVariable("categoryId") @Positive Long categoryId,
            @Valid @RequestBody ProductCategorySaveRequest request
    ) {
        return ApiResponse.success(
                productAdministrationService.updateCategory(categoryId, request)
        );
    }

    /**
     * 停用分类。
     *
     * @param categoryId 分类 ID
     * @return 空成功响应
     */
    @DeleteMapping("/categories/{categoryId}")
    public ApiResponse<Void> disableCategory(
            @PathVariable("categoryId") @Positive Long categoryId
    ) {
        productAdministrationService.disableCategory(categoryId);
        return ApiResponse.success(null);
    }

    /**
     * 查询窗口下所有菜品，包括已下架菜品。
     *
     * @param windowId 窗口 ID
     * @return 菜品列表
     */
    @GetMapping("/windows/{windowId}/products")
    public ApiResponse<List<ProductAdminVO>> listProducts(
            @PathVariable("windowId") @Positive Long windowId
    ) {
        return ApiResponse.success(
                productAdministrationService.listProducts(windowId)
        );
    }

    /**
     * 在窗口下创建菜品。
     *
     * @param windowId 窗口 ID
     * @param request 菜品字段
     * @return 新建菜品
     */
    @PostMapping("/windows/{windowId}/products")
    public ApiResponse<ProductAdminVO> createProduct(
            @PathVariable("windowId") @Positive Long windowId,
            @Valid @RequestBody ProductSaveRequest request
    ) {
        return ApiResponse.success(
                productAdministrationService.createProduct(windowId, request)
        );
    }

    /**
     * 查询菜品详情。
     *
     * @param productId 菜品 ID
     * @return 菜品详情
     */
    @GetMapping("/products/{productId}")
    public ApiResponse<ProductAdminVO> getProduct(
            @PathVariable("productId") @Positive Long productId
    ) {
        return ApiResponse.success(
                productAdministrationService.getProduct(productId)
        );
    }

    /**
     * 全量更新菜品。
     *
     * @param productId 菜品 ID
     * @param request 菜品字段
     * @return 更新后的菜品
     */
    @PutMapping("/products/{productId}")
    public ApiResponse<ProductAdminVO> updateProduct(
            @PathVariable("productId") @Positive Long productId,
            @Valid @RequestBody ProductSaveRequest request
    ) {
        return ApiResponse.success(
                productAdministrationService.updateProduct(productId, request)
        );
    }

    /**
     * 下架菜品。
     *
     * @param productId 菜品 ID
     * @return 空成功响应
     */
    @DeleteMapping("/products/{productId}")
    public ApiResponse<Void> disableProduct(
            @PathVariable("productId") @Positive Long productId
    ) {
        productAdministrationService.disableProduct(productId);
        return ApiResponse.success(null);
    }
}
