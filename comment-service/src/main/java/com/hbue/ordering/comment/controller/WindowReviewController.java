package com.hbue.ordering.comment.controller;

import com.hbue.ordering.comment.dto.CreateWindowReviewRequest;
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
    public WindowReviewController(WindowReviewService reviews) { this.reviews = reviews; }
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
            @RequestParam(name="cursor", required=false) String cursor, @RequestParam(name="limit", defaultValue="20") int limit) {
        return ApiResponse.success(reviews.page(Long.valueOf(jwt.getSubject()), null, cursor, limit));
    }
    @GetMapping("/windows/{windowId}")
    public ApiResponse<WindowReviewPageVO> window(@PathVariable("windowId") @Positive Long windowId,
            @RequestParam(name="cursor", required=false) String cursor, @RequestParam(name="limit", defaultValue="20") int limit) {
        return ApiResponse.success(reviews.page(null, windowId, cursor, limit));
    }
    @GetMapping("/windows/{windowId}/rating")
    public ApiResponse<com.hbue.ordering.comment.vo.WindowRatingVO> rating(@PathVariable("windowId") @Positive Long windowId) {
        return ApiResponse.success(reviews.rating(windowId));
    }
}
