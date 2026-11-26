package com.hbue.ordering.comment.controller;

import com.hbue.ordering.comment.api.ProductRatingVO;
import com.hbue.ordering.comment.dto.CreateCommentRequest;
import com.hbue.ordering.comment.dto.UpdateCommentRequest;
import com.hbue.ordering.comment.service.CommentService;
import com.hbue.ordering.comment.service.RatingSummaryService;
import com.hbue.ordering.comment.vo.CommentPageVO;
import com.hbue.ordering.comment.vo.CommentVO;
import com.hbue.ordering.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/comments")
@Validated
public class CommentController {
    private final CommentService comments;
    private final RatingSummaryService ratings;

    public CommentController(CommentService comments, RatingSummaryService ratings) {
        this.comments = comments;
        this.ratings = ratings;
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<CommentVO> create(@AuthenticationPrincipal Jwt jwt,
                                         @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody CreateCommentRequest body) {
        return ApiResponse.success(comments.create(Long.valueOf(jwt.getSubject()), jwt.getClaimAsString("username"),
                "Bearer " + jwt.getTokenValue(), key, body));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<CommentPageVO> mine(@AuthenticationPrincipal Jwt jwt,
                                           @RequestParam(name = "cursor", required = false) String cursor, @RequestParam(name = "limit", defaultValue = "20") int limit,
                                           @RequestParam(name = "score", required = false) Integer score,
                                           @RequestParam(name = "hasImages", required = false) Boolean hasImages) {
        return ApiResponse.success(comments.page(Long.valueOf(jwt.getSubject()), null, cursor, limit, score, hasImages));
    }

    @GetMapping("/products/{productId}")
    public ApiResponse<CommentPageVO> product(@PathVariable("productId") @Positive Long productId,
                                              @RequestParam(name = "cursor", required = false) String cursor, @RequestParam(name = "limit", defaultValue = "20") int limit,
                                              @RequestParam(name = "score", required = false) Integer score,
                                              @RequestParam(name = "hasImages", required = false) Boolean hasImages) {
        return ApiResponse.success(comments.page(null, productId, cursor, limit, score, hasImages));
    }

    @GetMapping("/products/{productId}/rating")
    public ApiResponse<ProductRatingVO> rating(@PathVariable("productId") @Positive Long productId) {
        return ApiResponse.success(ratings.get(productId));
    }

    @GetMapping("/{commentId}")
    public ApiResponse<CommentVO> detail(@PathVariable("commentId") String id) {
        return ApiResponse.success(comments.detail(id));
    }

    /**
     * 修改菜品评价。
     *
     * <p>角色注解只放行"可能管理评价"的五类角色，是否能改这一条由服务层判定：
     * 作者本人、系统管理员，或数据范围覆盖评价所属档口的管理角色。</p>
     *
     * @param jwt       当前用户令牌
     * @param commentId 公开评价标识
     * @param body      修改参数
     * @return 修改后的评价视图
     */
    @PutMapping("/{commentId}")
    @PreAuthorize("hasAnyRole('CUSTOMER','STALL_MANAGER','RESTAURANT_MANAGER','GENERAL_MANAGER','SYSTEM_ADMIN')")
    public ApiResponse<CommentVO> revise(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable("commentId") String commentId,
                                         @Valid @RequestBody UpdateCommentRequest body) {
        return ApiResponse.success(comments.revise(Long.valueOf(jwt.getSubject()), roles(jwt), commentId, body));
    }

    /**
     * 删除菜品评价（软删除）。
     *
     * <p>鉴权口径与修改一致；删除后评价立即从列表、评分与汇总中消失，记录保留用于追责。</p>
     *
     * @param jwt       当前用户令牌
     * @param commentId 公开评价标识
     * @return 删除成功响应
     */
    @DeleteMapping("/{commentId}")
    @PreAuthorize("hasAnyRole('CUSTOMER','STALL_MANAGER','RESTAURANT_MANAGER','GENERAL_MANAGER','SYSTEM_ADMIN')")
    public ApiResponse<Void> remove(@AuthenticationPrincipal Jwt jwt,
                                    @PathVariable("commentId") String commentId) {
        comments.remove(Long.valueOf(jwt.getSubject()), roles(jwt), commentId);
        return ApiResponse.success(null);
    }

    /**
     * 读取令牌中的角色编码。
     *
     * <p>角色声明由认证服务签发、经资源服务器验签后填充，属于服务端事实；
     * 缺失时返回空集合而不是抛异常，由服务层统一给出"只能操作自己的评价"的拒绝结果。</p>
     *
     * @param jwt 当前用户令牌
     * @return 角色编码集合，可能为空
     */
    private static java.util.List<String> roles(Jwt jwt) {
        java.util.List<String> roles = jwt.getClaimAsStringList("roles");
        return roles == null ? java.util.List.of() : roles;
    }

    /**
     * 只读全量概览；不返回用户标识，权限与既有公开评价查询保持一致。
     */
    @GetMapping("/products/{productId}/summary")
    public ApiResponse<com.hbue.ordering.comment.vo.ReviewSummaryVO> summary(
            @PathVariable("productId") @Positive Long productId) {
        return ApiResponse.success(comments.summary(productId));
    }
}
