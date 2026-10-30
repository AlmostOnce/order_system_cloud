package com.hbue.ordering.order.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.order.dto.request.OrderPickupVerifyRequest;
import com.hbue.ordering.order.service.OrderPickupService;
import com.hbue.ordering.order.vo.OrderPickupCodeVO;
import com.hbue.ordering.order.vo.OrderStatusVO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
public class OrderPickupController {
    private final OrderPickupService pickup;
    public OrderPickupController(OrderPickupService pickup) { this.pickup = pickup; }

    @GetMapping("/{orderId}/pickup-code")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<OrderPickupCodeVO> code(@AuthenticationPrincipal Jwt jwt, @PathVariable("orderId") Long orderId) {
        return ApiResponse.success(new OrderPickupCodeVO(pickup.getOwnPickupCode(Long.valueOf(jwt.getSubject()), orderId)));
    }

    @PostMapping("/pickup/verify")
    @PreAuthorize("hasAnyRole('STALL_MANAGER','RESTAURANT_MANAGER')")
    public ApiResponse<OrderStatusVO> verify(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody OrderPickupVerifyRequest request) {
        return ApiResponse.success(pickup.verifyPickup(Long.valueOf(jwt.getSubject()), request.pickupCode()));
    }
}
