package com.hbue.ordering.search.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.search.service.ProductSearchService;
import com.hbue.ordering.search.service.SearchIntentRecorder;
import com.hbue.ordering.search.vo.ProductSearchPageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/search")
@Tag(name = "菜品搜索")
public class ProductSearchController {
    /**
     * 搜索结果唯一来源；查询失败时不采集意图，也不伪造成功空结果。
     */
    private final ProductSearchService search;
    /**
     * 成功搜索后的有界异步采集，不成为搜索可用性的同步依赖。
     */
    private final SearchIntentRecorder intents;

    /**
     * 注入查询及弱信号采集，身份仅取资源服务器校验过的JWT，不接受userId请求参数。
     */
    public ProductSearchController(ProductSearchService search, SearchIntentRecorder intents) {
        this.search = search;
        this.intents = intents;
    }

    /**
     * 搜索当前可售菜品；显式queryId仅用于成功首屏的推荐意图去重，旧客户端和翻页不采集。
     * q、范围和分页保留现有约束；搜索成功后才入队，采集失败不会替换或延迟结果。
     *
     * @param queryId 16至64位主动提交标识；不带该参数时兼容原有只读搜索
     * @param jwt     经资源服务器验签及注销检查的当前学生JWT，原始subject为公开UUID
     * @return 原搜索服务的分页结果，排名和数量不因意图采集而改变
     */
    @GetMapping("/products")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "搜索当前可售菜品", description = "按菜品名、描述、分类、窗口和餐厅名称检索；可选餐厅、窗口和分类过滤。")
    public ApiResponse<ProductSearchPageVO> searchProducts(
            @RequestParam("q") @NotBlank @Size(max = 80) String keyword,
            @RequestParam(value = "restaurantId", required = false) @Min(1) Long restaurantId,
            @RequestParam(value = "windowId", required = false) @Min(1) Long windowId,
            @RequestParam(value = "categoryId", required = false) @Min(1) Long categoryId,
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(value = "queryId", required = false) @Pattern(regexp = "[A-Za-z0-9_-]{16,64}") String queryId,
            @AuthenticationPrincipal Jwt jwt) {
        ProductSearchPageVO result = search.search(keyword, restaurantId, windowId, categoryId, page, size);
        // 不重新查询ES、不遍历结果全量；后台最多记录成功首屏的前20名，空结果也保留真实意图。
        if (page == 1 && queryId != null)
            intents.recordSuccessfulFirstPage(jwt, queryId, result, restaurantId, windowId, categoryId);
        return ApiResponse.success(result);
    }

    /**
     * 输入联想：按前缀返回菜名候选，供搜索框下拉使用。
     *
     * <p>与正式搜索的区别：只匹配菜名字段、只返回菜名、不采集搜索意图。
     * 限频与缓存都在服务层完成；超限或 Redis 不可用时降级为空列表，
     * 前端表现为「暂无候选」，不影响用户继续提交正式搜索。</p>
     *
     * @param keyword 用户当前输入，最长 80 字符，与服务端搜索关键词约束一致
     * @param limit   候选数量，默认 8，最大 20
     * @param jwt     已验签的学生 JWT，subject 为公开 UUID，仅用于限频计数
     * @return 去重后的菜名列表，可能为空
     */
    @GetMapping("/suggest")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "菜名输入联想", description = "按菜名前缀返回去重后的候选菜名；仅学生可用，超限时返回空列表。")
    public ApiResponse<List<String>> suggestProducts(
            @RequestParam("keyword") @NotBlank @Size(max = 80) String keyword,
            @RequestParam(value = "limit", defaultValue = "8") @Min(1) @Max(20) int limit,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(search.suggest(keyword, limit, jwt.getSubject()));
    }
}
