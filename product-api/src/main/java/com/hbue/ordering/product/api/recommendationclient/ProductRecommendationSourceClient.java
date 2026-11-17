package com.hbue.ordering.product.api.recommendationclient;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.product.api.dto.ProductRecommendationDocument;
import com.hbue.ordering.product.api.dto.ProductRecommendationValidationRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

/** 商品服务面向推荐服务的受保护目录读取契约。 */
@FeignClient(name = "product-service", contextId = "productRecommendationSourceClient")
public interface ProductRecommendationSourceClient {
    /** 列出当前可检索窗口，供推荐目录回填按窗口有界读取。 */
    @GetMapping("/internal/products/recommendations/windows")
    ApiResponse<List<Long>> listWindowIds(@RequestHeader("X-Internal-Token") String token);

    /** 读取指定窗口当前有效商品及启用规格；每窗口一批，避免逐菜远程调用。 */
    @GetMapping("/internal/products/recommendations/windows/{windowId}/documents")
    ApiResponse<List<ProductRecommendationDocument>> loadWindowDocuments(
            @RequestHeader("X-Internal-Token") String token,
            @PathVariable("windowId") Long windowId);

    /** 刷新单个商品变更；空列表表示已下架、停用或删除，应从当前候选中移除。 */
    @GetMapping("/internal/products/recommendations/windows/{windowId}/products/{productId}")
    ApiResponse<List<ProductRecommendationDocument>> loadProductDocument(
            @RequestHeader("X-Internal-Token") String token,
            @PathVariable("windowId") Long windowId,
            @PathVariable("productId") Long productId);

    /** 最终返回首页前批量复核已排序候选，防止本地目录刷新间隔内继续展示已下架菜品。 */
    @PostMapping("/internal/products/recommendations/validate")
    ApiResponse<List<ProductRecommendationDocument>> validateCandidates(
            @RequestHeader("X-Internal-Token") String token,
            @RequestBody ProductRecommendationValidationRequest request);
}
