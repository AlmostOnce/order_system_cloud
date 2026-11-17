package com.hbue.ordering.product.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.common.web.security.InternalScopeTokenVerifier;
import com.hbue.ordering.product.api.dto.ProductRecommendationDocument;
import com.hbue.ordering.product.api.dto.ProductRecommendationValidationRequest;
import com.hbue.ordering.product.service.ProductRecommendationSourceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 只允许带内部共享凭据的推荐服务读取当前商品目录，不经 API 网关暴露。 */
@RestController
@RequestMapping("/internal/products/recommendations")
public class ProductRecommendationSourceController {
    private final ProductRecommendationSourceService source;
    private final InternalScopeTokenVerifier verifier;

    /** 注入目录查询和统一内部令牌校验器，所有 handler 在查询前验证调用方。 */
    public ProductRecommendationSourceController(ProductRecommendationSourceService source,
            InternalScopeTokenVerifier verifier) {
        this.source = source;
        this.verifier = verifier;
    }

    /** 返回当前有效窗口编号；接口不接受客户端传入用户身份或展示范围。 */
    @GetMapping("/windows")
    public ApiResponse<List<Long>> windows(
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        verifier.verify(token);
        return ApiResponse.success(source.windowIds());
    }

    /** 返回窗口当前可售菜品及可选规格，规格选项只表示可供选择而非顾客偏好。 */
    @GetMapping("/windows/{windowId}/documents")
    public ApiResponse<List<ProductRecommendationDocument>> documents(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @PathVariable Long windowId) {
        verifier.verify(token);
        return ApiResponse.success(source.documents(windowId));
    }

    /** 返回单个商品的当前快照；下架或失效商品以空数组表示，调用方需撤下旧候选。 */
    @GetMapping("/windows/{windowId}/products/{productId}")
    public ApiResponse<List<ProductRecommendationDocument>> product(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @PathVariable Long windowId, @PathVariable Long productId) {
        verifier.verify(token);
        return ApiResponse.success(source.document(windowId, productId));
    }

    /** 批量复核首页候选的当前售卖、基础价和启用规格，失效商品从结果中省略。 */
    @PostMapping("/validate")
    public ApiResponse<List<ProductRecommendationDocument>> validate(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody ProductRecommendationValidationRequest request) {
        verifier.verify(token);
        return ApiResponse.success(source.validateCandidates(request));
    }
}
