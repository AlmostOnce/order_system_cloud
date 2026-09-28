package com.hbue.ordering.product.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.product.api.dto.ProductQuoteRequest;
import com.hbue.ordering.product.api.dto.ProductQuoteResponse;
import com.hbue.ordering.product.service.ProductQuoteService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仅供后端服务调用的商品核价接口。
 *
 * @author order-system
 * @date 2026-09-28
 */
@RestController
@RequestMapping("/internal/products")
public class ProductInternalController {

    /** 商品核价业务服务。 */
    private final ProductQuoteService productQuoteService;

    /**
     * 创建商品核价 Controller。
     *
     * @param productQuoteService 商品核价业务服务
     */
    public ProductInternalController(ProductQuoteService productQuoteService) {
        this.productQuoteService = productQuoteService;
    }

    /**
     * 返回指定窗口和菜品的可下单状态及当前价格。
     *
     * @param request 核价请求
     * @return 商品核价响应
     */
    @PostMapping("/quote")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<ProductQuoteResponse> quote(
            @Valid @RequestBody ProductQuoteRequest request
    ) {
        return ApiResponse.success(productQuoteService.quote(request));
    }
}
