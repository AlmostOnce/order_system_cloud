package com.hbue.ordering.comment.controller;

import com.hbue.ordering.comment.service.OrderReviewService;
import com.hbue.ordering.comment.vo.OrderReviewsVO;
import com.hbue.ordering.common.response.ApiResponse;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/comments/orders")
@Validated
public class OrderReviewController {
    private final OrderReviewService reviews;
    public OrderReviewController(OrderReviewService reviews) { this.reviews = reviews; }
    @GetMapping("/{orderId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<OrderReviewsVO> get(@AuthenticationPrincipal Jwt jwt,
            @PathVariable("orderId") @Positive Long orderId) {
        return ApiResponse.success(reviews.get(Long.valueOf(jwt.getSubject()), orderId, "Bearer " + jwt.getTokenValue()));
    }
}
