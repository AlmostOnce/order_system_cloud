package com.hbue.ordering.product.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.common.web.security.InternalScopeTokenVerifier;
import com.hbue.ordering.product.api.dto.ProductSearchDocument;
import com.hbue.ordering.product.service.ProductSearchSourceService;
import com.hbue.ordering.product.api.dto.ProductQuoteRequest;
import com.hbue.ordering.product.api.dto.ProductQuoteResponse;
import com.hbue.ordering.product.service.ProductQuoteService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.List;

/**
 * 仅供后端服务调用的商品核价接口。
 *
 * @author order-system
 * @date 2026-09-28
 */
@RestController
@Tag(name = "内部接口（服务间调用）")
@RequestMapping("/internal/products")
public class ProductInternalController {

    /** 商品核价业务服务。 */
    private final ProductQuoteService productQuoteService;
    private final ProductSearchSourceService searchSource;
    private final InternalScopeTokenVerifier internalTokenVerifier;

    /**
     * 创建商品核价 Controller。
     *
     * @param productQuoteService 商品核价业务服务
     */
    public ProductInternalController(ProductQuoteService productQuoteService,
                                     ProductSearchSourceService searchSource,
                                     InternalScopeTokenVerifier internalTokenVerifier) {
        this.productQuoteService = productQuoteService;
        this.searchSource = searchSource;
        this.internalTokenVerifier = internalTokenVerifier;
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

    @GetMapping("/search/windows")
    public ApiResponse<List<Long>> searchableWindows(@RequestHeader(value = "X-Internal-Token", required = false) String token) {
        internalTokenVerifier.verify(token);
        return ApiResponse.success(searchSource.searchableWindowIds());
    }

    @GetMapping("/search/windows/{windowId}/documents")
    public ApiResponse<List<ProductSearchDocument>> searchDocuments(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @PathVariable("windowId") Long windowId) {
        internalTokenVerifier.verify(token);
        return ApiResponse.success(searchSource.documents(windowId));
    }
}
