package com.hbue.ordering.comment.controller;
import com.hbue.ordering.comment.api.ProductRatingVO;
import com.hbue.ordering.comment.dto.CreateCommentRequest;
import com.hbue.ordering.comment.service.*;
import com.hbue.ordering.comment.vo.*;
import com.hbue.ordering.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/comments")
@Validated
public class CommentController {
    private final CommentService comments;
    private final RatingSummaryService ratings;
    public CommentController(CommentService comments,RatingSummaryService ratings) {this.comments=comments;this.ratings=ratings;}
    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<CommentVO> create(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String key,@Valid @RequestBody CreateCommentRequest body) {
        return ApiResponse.success(comments.create(Long.valueOf(jwt.getSubject()),jwt.getClaimAsString("username"),
                "Bearer "+jwt.getTokenValue(),key,body));
    }
    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<CommentPageVO> mine(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(name="cursor",required=false) String cursor,@RequestParam(name="limit",defaultValue="20") int limit,
            @RequestParam(name="score", required=false) Integer score,
            @RequestParam(name="hasImages", required=false) Boolean hasImages) {
        return ApiResponse.success(comments.page(Long.valueOf(jwt.getSubject()),null,cursor,limit,score,hasImages));
    }
    @GetMapping("/products/{productId}")
    public ApiResponse<CommentPageVO> product(@PathVariable("productId") @Positive Long productId,
            @RequestParam(name="cursor",required=false) String cursor,@RequestParam(name="limit",defaultValue="20") int limit,
            @RequestParam(name="score", required=false) Integer score,
            @RequestParam(name="hasImages", required=false) Boolean hasImages) {
        return ApiResponse.success(comments.page(null,productId,cursor,limit,score,hasImages));
    }
    @GetMapping("/products/{productId}/rating")
    public ApiResponse<ProductRatingVO> rating(@PathVariable("productId") @Positive Long productId) {
        return ApiResponse.success(ratings.get(productId));
    }
    @GetMapping("/{commentId}")
    public ApiResponse<CommentVO> detail(@PathVariable("commentId") String id) {return ApiResponse.success(comments.detail(id));}
    /** 只读全量概览；不返回用户标识，权限与既有公开评价查询保持一致。 */
    @GetMapping("/products/{productId}/summary")
    public ApiResponse<com.hbue.ordering.comment.vo.ReviewSummaryVO> summary(
            @PathVariable("productId") @Positive Long productId) {
        return ApiResponse.success(comments.summary(productId));
    }
}
