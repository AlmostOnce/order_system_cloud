package com.hbue.ordering.search.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.common.web.security.InternalScopeTokenVerifier;
import com.hbue.ordering.search.service.ProductSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 不经 Gateway 暴露的搜索索引维护接口。 */
@RestController
@RequestMapping("/internal/search")
@Tag(name = "搜索内部维护接口")
public class SearchInternalController {
    private final ProductSearchService search;
    private final InternalScopeTokenVerifier tokenVerifier;

    public SearchInternalController(ProductSearchService search, InternalScopeTokenVerifier tokenVerifier) {
        this.search = search;
        this.tokenVerifier = tokenVerifier;
    }

    @PostMapping("/rebuild")
    @Operation(summary = "从商品主库全量重建菜品索引", description = "仅供受信任服务直连，使用 X-Internal-Token。")
    public ApiResponse<Integer> rebuild(@RequestHeader(value = "X-Internal-Token", required = false) String token) {
        tokenVerifier.verify(token);
        return ApiResponse.success(search.rebuildAll());
    }
}
