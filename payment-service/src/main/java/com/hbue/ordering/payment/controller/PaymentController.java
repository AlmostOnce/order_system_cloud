package com.hbue.ordering.payment.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.payment.dto.request.PaymentCreateRequest;
import com.hbue.ordering.payment.service.PaymentService;
import com.hbue.ordering.payment.vo.PaymentCreateVO;
import com.hbue.ordering.payment.vo.PaymentStatusVO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付服务顾客接口。
 *
 * <p>顾客侧没有"标记支付成功"端点——订单支付状态只能由渠道回调事件驱动。</p>
 *
 * @author order-system
 * @date 2026-10-12
 */
@RestController
@RequestMapping("/payments")
public class PaymentController {

    /** 支付业务服务。 */
    private final PaymentService paymentService;

    /**
     * 创建支付服务顾客接口。
     *
     * @param paymentService 支付业务服务
     */
    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * 为待支付订单创建支付单。
     *
     * @param jwt 当前登录顾客 JWT
     * @param request 创建支付单请求
     * @return 支付单创建结果
     */
    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<PaymentCreateVO> createPayment(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PaymentCreateRequest request
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        return ApiResponse.success(
                paymentService.createPayment(userId, request.getOrderId())
        );
    }

    /**
     * 查询支付单状态。
     *
     * @param jwt 当前登录顾客 JWT
     * @param paymentId 支付单 ID
     * @return 支付单状态
     */
    @GetMapping("/{paymentId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<PaymentStatusVO> getPaymentStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("paymentId") Long paymentId
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        return ApiResponse.success(
                paymentService.getPaymentStatus(userId, paymentId)
        );
    }

    /**
     * 按订单查询最近一笔支付单（前端轮询用）。
     *
     * @param jwt 当前登录顾客 JWT
     * @param orderId 订单 ID
     * @return 支付单状态
     */
    @GetMapping("/order/{orderId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<PaymentStatusVO> getLatestByOrder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("orderId") Long orderId
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        return ApiResponse.success(
                paymentService.getLatestByOrder(userId, orderId)
        );
    }
}
