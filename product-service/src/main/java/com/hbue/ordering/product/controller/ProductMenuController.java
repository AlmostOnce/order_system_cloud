package com.hbue.ordering.product.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.product.service.ProductMenuService;
import com.hbue.ordering.product.vo.ProductImageVO;
import com.hbue.ordering.product.vo.RestaurantSummaryVO;
import com.hbue.ordering.product.vo.WindowMenuVO;
import com.hbue.ordering.product.vo.WindowSummaryVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 顾客窗口与菜单查询接口。
 *
 * @author order-system
 * @date 2026-09-28
 */
@Validated
@RestController
@Tag(name = "商品菜单接口")
@RequestMapping("/products")
@PreAuthorize("hasRole('CUSTOMER')")
public class ProductMenuController {

    /**
     * 最多50道菜一次查询，避免榜单逐项请求完整菜单。
     */
    @GetMapping("/images")
    public ApiResponse<List<ProductImageVO>> productImages(
            @RequestParam("productIds")
            @Size(min = 1, max = 50) List<@Positive Long> productIds) {
        return ApiResponse.success(productMenuService.getProductImages(productIds));
    }

    /**
     * 窗口菜单查询服务。
     */
    private final ProductMenuService productMenuService;

    /**
     * 创建窗口菜单 Controller。
     *
     * @param productMenuService 窗口菜单查询服务
     */
    public ProductMenuController(ProductMenuService productMenuService) {
        this.productMenuService = productMenuService;
    }

    /**
     * 查询所有当前启用的餐厅。
     *
     * @return 启用餐厅摘要列表，已按展示排序值升序
     */
    @GetMapping("/restaurants")
    public ApiResponse<List<RestaurantSummaryVO>> listActiveRestaurants() {
        return ApiResponse.success(productMenuService.listActiveRestaurants());
    }

    /**
     * 查询所有当前启用的窗口。
     *
     * @return 启用窗口摘要列表，已按所属餐厅展示排序值排序
     */
    @GetMapping("/windows")
    public ApiResponse<List<WindowSummaryVO>> listActiveWindows() {
        return ApiResponse.success(productMenuService.listActiveWindows());
    }

    /**
     * 查询指定窗口当前可供顾客下单的菜单。
     *
     * @param windowId 窗口 ID
     * @return 窗口菜单
     */
    @GetMapping("/windows/{windowId}/menu")
    public ApiResponse<WindowMenuVO> getWindowMenu(
            @PathVariable("windowId") @Positive Long windowId
    ) {
        return ApiResponse.success(productMenuService.getWindowMenu(windowId));
    }

    /**
     * 查询单个窗口的展示摘要，供订单详情展示取餐地点。
     *
     * <p>与菜单接口相互独立：订单详情用它在窗口停用或休息时也能回看取餐地点；
     * 查询失败只影响地点行展示，不影响订单正文、取餐码和支付。</p>
     *
     * @param windowId 窗口 ID
     * @return 窗口摘要；窗口不存在时 data 为 null
     */
    @GetMapping("/windows/{windowId}")
    public ApiResponse<WindowSummaryVO> getWindowSummary(
            @PathVariable("windowId") @Positive Long windowId
    ) {
        return ApiResponse.success(productMenuService.getWindowSummary(windowId));
    }
}
