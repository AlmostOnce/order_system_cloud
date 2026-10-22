package com.hbue.ordering.product.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.product.service.ProductStockService;
import com.hbue.ordering.product.vo.ProductStockVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 顾客库存查询入口；沿用商品路由，内部库存接口不对外开放。 */
@Validated
@RestController
@Tag(name = "商品菜单接口")
@RequestMapping("/products/windows")
@PreAuthorize("hasRole('CUSTOMER')")
public class ProductStockController {
    private final ProductStockService productStockService;

    public ProductStockController(ProductStockService productStockService) {
        this.productStockService = productStockService;
    }

    @GetMapping("/{windowId}/stocks")
    public ApiResponse<List<ProductStockVO>> getWindowStocks(
            @PathVariable("windowId") @Positive Long windowId) {
        return ApiResponse.success(productStockService.getWindowStocks(windowId));
    }
}
