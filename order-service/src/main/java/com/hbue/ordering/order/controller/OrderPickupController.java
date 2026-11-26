package com.hbue.ordering.order.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.order.dto.request.OrderPickupVerifyRequest;
import com.hbue.ordering.order.service.OrderPickupService;
import com.hbue.ordering.order.vo.OrderPickupCodeVO;
import com.hbue.ordering.order.vo.OrderPickupQrCodeVO;
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

    public OrderPickupController(OrderPickupService pickup) {
        this.pickup = pickup;
    }

    @GetMapping("/{orderId}/pickup-code")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<OrderPickupCodeVO> code(@AuthenticationPrincipal Jwt jwt, @PathVariable("orderId") Long orderId) {
        return ApiResponse.success(new OrderPickupCodeVO(pickup.getOwnPickupCode(Long.valueOf(jwt.getSubject()), orderId)));
    }

    /**
     * 返回订单取餐码对应的 PNG 二维码，供学生端直接展示。
     *
     * <p>二维码内容与数字取餐码一致，权限、状态与退款校验全部复用
     * {@link OrderPickupService#getOwnPickupCode(Long, Long)}；窗口扫码枪扫出的就是六位数字，
     * 现有核销接口无需调整。</p>
     */
    @GetMapping("/{orderId}/pickup-qrcode")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<OrderPickupQrCodeVO> qrCode(@AuthenticationPrincipal Jwt jwt, @PathVariable("orderId") Long orderId) {
        return ApiResponse.success(new OrderPickupQrCodeVO(
                pickup.getOwnPickupQrCode(Long.valueOf(jwt.getSubject()), orderId)));
    }

    @PostMapping("/pickup/verify")
    @PreAuthorize("hasAnyRole('STALL_MANAGER','RESTAURANT_MANAGER')")
    public ApiResponse<OrderStatusVO> verify(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody OrderPickupVerifyRequest request) {
        return ApiResponse.success(pickup.verifyPickup(Long.valueOf(jwt.getSubject()), request.pickupCode()));
    }
}
