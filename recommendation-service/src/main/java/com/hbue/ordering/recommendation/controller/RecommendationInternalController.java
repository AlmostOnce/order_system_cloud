package com.hbue.ordering.recommendation.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.common.web.security.InternalScopeTokenVerifier;
import com.hbue.ordering.recommendation.dto.SearchIntentRequest;
import com.hbue.ordering.recommendation.service.RecommendationSearchIntentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 搜索服务的内部采集入口；/internal 不配置网关路由，仍强制校验共享凭据。
 */
@RestController
@RequestMapping("/internal/recommendations")
public class RecommendationInternalController {
    private final InternalScopeTokenVerifier verifier;
    private final RecommendationSearchIntentService searches;

    /**
     * 注入内部令牌校验及短期搜索写入服务。
     */
    public RecommendationInternalController(InternalScopeTokenVerifier verifier,
                                            RecommendationSearchIntentService searches) {
        this.verifier = verifier;
        this.searches = searches;
    }

    /**
     * 只接收搜索服务端确认成功的首屏结果；内部用户编号必须来自其验签身份解析。
     */
    @PostMapping("/search-intents")
    public ApiResponse<Void> recordSearchIntent(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @Valid @RequestBody SearchIntentRequest request) {
        verifier.verify(token);
        searches.record(request);
        return ApiResponse.success(null);
    }
}
