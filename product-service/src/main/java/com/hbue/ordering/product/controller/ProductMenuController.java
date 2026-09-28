package com.hbue.ordering.product.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.product.service.ProductMenuService;
import com.hbue.ordering.product.vo.WindowMenuVO;
import com.hbue.ordering.product.vo.WindowSummaryVO;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 顾客窗口与菜单查询接口。
 *
 * @author order-system
 * @date 2026-09-28
 */
@Validated
@RestController
@RequestMapping("/products/windows")
@PreAuthorize("hasRole('CUSTOMER')")
public class ProductMenuController {

    /** 窗口菜单查询服务。 */
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
     * 查询所有当前启用的窗口。
     *
     * @return 启用窗口摘要列表
     */
    @GetMapping
    public ApiResponse<List<WindowSummaryVO>> listActiveWindows() {
        return ApiResponse.success(productMenuService.listActiveWindows());
    }

    /**
     * 查询指定窗口当前可供顾客下单的菜单。
     *
     * @param windowId 窗口 ID
     * @return 窗口菜单
     */
    @GetMapping("/{windowId}/menu")
    public ApiResponse<WindowMenuVO> getWindowMenu(
            @PathVariable("windowId") @Positive Long windowId
    ) {
        return ApiResponse.success(productMenuService.getWindowMenu(windowId));
    }
}
