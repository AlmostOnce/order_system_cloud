package com.hbue.ordering.product.api.client;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.product.api.client.fallback.ProductServiceClientFallbackFactory;
import com.hbue.ordering.product.api.dto.ProductQuoteRequest;
import com.hbue.ordering.product.api.dto.ProductQuoteResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 商品服务内部核价客户端。
 *
 * @author order-system
 * @date 2026-09-28
 */
@FeignClient(
        name = "product-service",
        fallbackFactory = ProductServiceClientFallbackFactory.class
)
public interface ProductServiceClient {

    /**
     * 批量核验窗口和菜品状态并取得当前价格。
     *
     * @param request 窗口 ID 和菜品 ID 列表
     * @return 商品核价响应
     */
    @PostMapping("/internal/products/quote")
    ApiResponse<ProductQuoteResponse> quote(
            @RequestBody ProductQuoteRequest request
    );
}
