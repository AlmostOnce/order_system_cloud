package com.hbue.ordering.search.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.search.model.ProductSearchPage;
import com.hbue.ordering.search.service.ProductSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/search")
@Tag(name = "菜品搜索")
public class ProductSearchController {
    private final ProductSearchService search;
    public ProductSearchController(ProductSearchService search) {
        this.search = search;
    }

    @GetMapping("/products")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "搜索当前可售菜品", description = "按菜品名、描述、分类、窗口和餐厅名称检索；可选餐厅、窗口和分类过滤。")
    public ApiResponse<ProductSearchPage> searchProducts(
            @RequestParam("q") @NotBlank @Size(max = 80) String keyword,
            @RequestParam(value = "restaurantId", required = false) @Min(1) Long restaurantId,
            @RequestParam(value = "windowId", required = false) @Min(1) Long windowId,
            @RequestParam(value = "categoryId", required = false) @Min(1) Long categoryId,
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(search.search(keyword, restaurantId, windowId, categoryId, page, size));
    }
}
