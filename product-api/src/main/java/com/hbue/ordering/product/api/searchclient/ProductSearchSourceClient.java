package com.hbue.ordering.product.api.searchclient;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.product.api.dto.ProductSearchDocument;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

/** 搜索服务读取商品主数据的内部契约。 */
@FeignClient(name = "product-service", contextId = "productSearchSourceClient")
public interface ProductSearchSourceClient {
    @GetMapping("/internal/products/search/windows")
    ApiResponse<List<Long>> listSearchableWindowIds(@RequestHeader("X-Internal-Token") String token);

    @GetMapping("/internal/products/search/windows/{windowId}/documents")
    ApiResponse<List<ProductSearchDocument>> loadWindowDocuments(@RequestHeader("X-Internal-Token") String token,
                                                                  @PathVariable("windowId") Long windowId);

    @GetMapping("/internal/products/search/windows/{windowId}/products/{productId}")
    ApiResponse<List<ProductSearchDocument>> loadProductDocument(@RequestHeader("X-Internal-Token") String token,
            @PathVariable("windowId") Long windowId, @PathVariable("productId") Long productId);
}
