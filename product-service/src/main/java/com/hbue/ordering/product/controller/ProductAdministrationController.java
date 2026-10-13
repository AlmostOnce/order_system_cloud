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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
 * <p>角色门禁只放行档口负责人与餐厅经理；技术角色 {@code SYSTEM_ADMIN}
 * 不参与业务数据管理。每个接口还会把当前操作者传给 Service，
 * 由 Service 完成数据范围校验（档口、菜单属公开资源，越权返回 403）。</p>
 *
 * @author order-system
 * @date 2026-09-28
 */
@Validated
@RestController
@RequestMapping("/products/admin")
@PreAuthorize("hasAnyRole('STALL_MANAGER','RESTAURANT_MANAGER')")
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
     * 查询操作者范围内的窗口，包括已停用窗口。
     *
     * @param jwt 当前登录操作者 JWT
     * @return 窗口列表
     */
    @GetMapping("/windows")
    public ApiResponse<List<WindowAdminVO>> listWindows(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.success(
                productAdministrationService.listWindows(operatorId(jwt))
        );
    }

    /**
     * 在操作者范围内的餐厅创建窗口。
     *
     * @param jwt 当前登录操作者 JWT
     * @param request 窗口字段
     * @return 新建窗口
     */
    @PostMapping("/windows")
    public ApiResponse<WindowAdminVO> createWindow(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody WindowSaveRequest request
    ) {
        return ApiResponse.success(
                productAdministrationService.createWindow(
                        operatorId(jwt),
                        request
                )
        );
    }

    /**
     * 全量更新窗口。
     *
     * @param jwt 当前登录操作者 JWT
     * @param windowId 窗口 ID
     * @param request 窗口字段
     * @return 更新后的窗口
     */
    @PutMapping("/windows/{windowId}")
    public ApiResponse<WindowAdminVO> updateWindow(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("windowId") @Positive Long windowId,
            @Valid @RequestBody WindowSaveRequest request
    ) {
        return ApiResponse.success(
                productAdministrationService.updateWindow(
                        operatorId(jwt),
                        windowId,
                        request
                )
        );
    }

    /**
     * 停用窗口。
     *
     * @param jwt 当前登录操作者 JWT
     * @param windowId 窗口 ID
     * @return 空成功响应
     */
    @DeleteMapping("/windows/{windowId}")
    public ApiResponse<Void> disableWindow(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("windowId") @Positive Long windowId
    ) {
        productAdministrationService.disableWindow(operatorId(jwt), windowId);
        return ApiResponse.success(null);
    }

    /**
     * 查询窗口下所有分类，包括已停用分类。
     *
     * @param jwt 当前登录操作者 JWT
     * @param windowId 窗口 ID
     * @return 分类列表
     */
    @GetMapping("/windows/{windowId}/categories")
    public ApiResponse<List<ProductCategoryAdminVO>> listCategories(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("windowId") @Positive Long windowId
    ) {
        return ApiResponse.success(
                productAdministrationService.listCategories(
                        operatorId(jwt),
                        windowId
                )
        );
    }

    /**
     * 在窗口下创建分类。
     *
     * @param jwt 当前登录操作者 JWT
     * @param windowId 窗口 ID
     * @param request 分类字段
     * @return 新建分类
     */
    @PostMapping("/windows/{windowId}/categories")
    public ApiResponse<ProductCategoryAdminVO> createCategory(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("windowId") @Positive Long windowId,
            @Valid @RequestBody ProductCategorySaveRequest request
    ) {
        return ApiResponse.success(
                productAdministrationService.createCategory(
                        operatorId(jwt),
                        windowId,
                        request
                )
        );
    }

    /**
     * 全量更新分类。
     *
     * @param jwt 当前登录操作者 JWT
     * @param categoryId 分类 ID
     * @param request 分类字段
     * @return 更新后的分类
     */
    @PutMapping("/categories/{categoryId}")
    public ApiResponse<ProductCategoryAdminVO> updateCategory(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("categoryId") @Positive Long categoryId,
            @Valid @RequestBody ProductCategorySaveRequest request
    ) {
        return ApiResponse.success(
                productAdministrationService.updateCategory(
                        operatorId(jwt),
                        categoryId,
                        request
                )
        );
    }

    /**
     * 停用分类。
     *
     * @param jwt 当前登录操作者 JWT
     * @param categoryId 分类 ID
     * @return 空成功响应
     */
    @DeleteMapping("/categories/{categoryId}")
    public ApiResponse<Void> disableCategory(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("categoryId") @Positive Long categoryId
    ) {
        productAdministrationService.disableCategory(
                operatorId(jwt),
                categoryId
        );
        return ApiResponse.success(null);
    }

    /**
     * 查询窗口下所有菜品，包括已下架菜品。
     *
     * @param jwt 当前登录操作者 JWT
     * @param windowId 窗口 ID
     * @return 菜品列表
     */
    @GetMapping("/windows/{windowId}/products")
    public ApiResponse<List<ProductAdminVO>> listProducts(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("windowId") @Positive Long windowId
    ) {
        return ApiResponse.success(
                productAdministrationService.listProducts(
                        operatorId(jwt),
                        windowId
                )
        );
    }

    /**
     * 在窗口下创建菜品。
     *
     * @param jwt 当前登录操作者 JWT
     * @param windowId 窗口 ID
     * @param request 菜品字段
     * @return 新建菜品
     */
    @PostMapping("/windows/{windowId}/products")
    public ApiResponse<ProductAdminVO> createProduct(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("windowId") @Positive Long windowId,
            @Valid @RequestBody ProductSaveRequest request
    ) {
        return ApiResponse.success(
                productAdministrationService.createProduct(
                        operatorId(jwt),
                        windowId,
                        request
                )
        );
    }

    /**
     * 查询菜品详情。
     *
     * @param jwt 当前登录操作者 JWT
     * @param productId 菜品 ID
     * @return 菜品详情
     */
    @GetMapping("/products/{productId}")
    public ApiResponse<ProductAdminVO> getProduct(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("productId") @Positive Long productId
    ) {
        return ApiResponse.success(
                productAdministrationService.getProduct(
                        operatorId(jwt),
                        productId
                )
        );
    }

    /**
     * 全量更新菜品。
     *
     * @param jwt 当前登录操作者 JWT
     * @param productId 菜品 ID
     * @param request 菜品字段
     * @return 更新后的菜品
     */
    @PutMapping("/products/{productId}")
    public ApiResponse<ProductAdminVO> updateProduct(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("productId") @Positive Long productId,
            @Valid @RequestBody ProductSaveRequest request
    ) {
        return ApiResponse.success(
                productAdministrationService.updateProduct(
                        operatorId(jwt),
                        productId,
                        request
                )
        );
    }

    /**
     * 下架菜品。
     *
     * @param jwt 当前登录操作者 JWT
     * @param productId 菜品 ID
     * @return 空成功响应
     */
    @DeleteMapping("/products/{productId}")
    public ApiResponse<Void> disableProduct(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("productId") @Positive Long productId
    ) {
        productAdministrationService.disableProduct(operatorId(jwt), productId);
        return ApiResponse.success(null);
    }

    /**
     * 从 JWT 中取出当前操作者 ID。
     *
     * @param jwt 当前登录操作者 JWT
     * @return 操作者用户 ID
     */
    private Long operatorId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
