package com.hbue.ordering.comment.controller;

import com.hbue.ordering.comment.dto.CreateWindowReviewRequest;
import com.hbue.ordering.comment.dto.UpdateWindowReviewRequest;
import com.hbue.ordering.comment.service.WindowReviewService;
import com.hbue.ordering.comment.vo.WindowReviewPageVO;
import com.hbue.ordering.comment.vo.WindowReviewVO;
import com.hbue.ordering.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/comments/window-reviews")
@Validated
public class WindowReviewController {
    private final WindowReviewService reviews;

    public WindowReviewController(WindowReviewService reviews) {
        this.reviews = reviews;
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<WindowReviewVO> create(@AuthenticationPrincipal Jwt jwt,
                                              @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody CreateWindowReviewRequest body) {
        return ApiResponse.success(reviews.create(Long.valueOf(jwt.getSubject()), jwt.getClaimAsString("username"),
                "Bearer " + jwt.getTokenValue(), key, body));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<WindowReviewPageVO> mine(@AuthenticationPrincipal Jwt jwt,
                                                @RequestParam(name = "cursor", required = false) String cursor, @RequestParam(name = "limit", defaultValue = "20") int limit,
                                                @RequestParam(name = "score", required = false) Integer score,
                                                @RequestParam(name = "hasImages", required = false) Boolean hasImages) {
        return ApiResponse.success(reviews.page(Long.valueOf(jwt.getSubject()), null, cursor, limit, score, hasImages));
    }

    @GetMapping("/windows/{windowId}")
    public ApiResponse<WindowReviewPageVO> window(@PathVariable("windowId") @Positive Long windowId,
                                                  @RequestParam(name = "cursor", required = false) String cursor, @RequestParam(name = "limit", defaultValue = "20") int limit,
                                                  @RequestParam(name = "score", required = false) Integer score,
                                                  @RequestParam(name = "hasImages", required = false) Boolean hasImages) {
        return ApiResponse.success(reviews.page(null, windowId, cursor, limit, score, hasImages));
    }

    /**
     * 修改店铺评价。
     *
     * <p>角色注解只放行"可能管理评价"的五类角色；是否能改这一条由服务层判定：
     * 作者本人、系统管理员，或数据范围覆盖该档口的管理角色。</p>
     *
     * @param jwt      当前用户令牌
     * @param reviewId 公开评价标识
     * @param body     修改参数
     * @return 修改后的评价视图
     */
    @PutMapping("/{reviewId}")
    @PreAuthorize("hasAnyRole('CUSTOMER','STALL_MANAGER','RESTAURANT_MANAGER','GENERAL_MANAGER','SYSTEM_ADMIN')")
    public ApiResponse<WindowReviewVO> revise(@AuthenticationPrincipal Jwt jwt,
                                              @PathVariable("reviewId") String reviewId,
                                              @Valid @RequestBody UpdateWindowReviewRequest body) {
        return ApiResponse.success(reviews.revise(Long.valueOf(jwt.getSubject()), roles(jwt), reviewId, body));
    }

    /**
     * 删除店铺评价（软删除）。
     *
     * <p>鉴权口径与修改一致；删除后评价立即从列表、评分与汇总中消失，记录保留用于追责。</p>
     *
     * @param jwt      当前用户令牌
     * @param reviewId 公开评价标识
     * @return 删除成功响应
     */
    @DeleteMapping("/{reviewId}")
    @PreAuthorize("hasAnyRole('CUSTOMER','STALL_MANAGER','RESTAURANT_MANAGER','GENERAL_MANAGER','SYSTEM_ADMIN')")
    public ApiResponse<Void> remove(@AuthenticationPrincipal Jwt jwt,
                                    @PathVariable("reviewId") String reviewId) {
        reviews.remove(Long.valueOf(jwt.getSubject()), roles(jwt), reviewId);
        return ApiResponse.success(null);
    }

    /**
     * 读取令牌中的角色编码。
     *
     * <p>角色声明由认证服务签发、经资源服务器验签后填充，属于服务端事实；
     * 缺失时返回空集合，由服务层统一给出"只能操作自己的评价"的拒绝结果。</p>
     *
     * @param jwt 当前用户令牌
     * @return 角色编码集合，可能为空
     */
    private static java.util.List<String> roles(Jwt jwt) {
        java.util.List<String> roles = jwt.getClaimAsStringList("roles");
        return roles == null ? java.util.List.of() : roles;
    }

    @GetMapping("/windows/{windowId}/rating")
    public ApiResponse<com.hbue.ordering.comment.vo.WindowRatingVO> rating(@PathVariable("windowId") @Positive Long windowId) {
        return ApiResponse.success(reviews.rating(windowId));
    }

    /**
     * 只读全量概览；不返回用户标识，权限与既有公开评价查询保持一致。
     */
    @GetMapping("/windows/{windowId}/summary")
    public ApiResponse<com.hbue.ordering.comment.vo.ReviewSummaryVO> summary(
            @PathVariable("windowId") @Positive Long windowId) {
        return ApiResponse.success(reviews.summary(windowId));
    }
}
