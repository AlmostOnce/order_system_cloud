package com.hbue.ordering.payment.controller;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.common.web.security.InternalScopeTokenVerifier;
import com.hbue.ordering.payment.service.PaymentService;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/internal/payments/orders")
public class PaymentCloseInternalController {
    private final PaymentService payments;
    private final InternalScopeTokenVerifier tokens;
    public PaymentCloseInternalController(PaymentService payments,InternalScopeTokenVerifier tokens) {this.payments=payments;this.tokens=tokens;}
    @PostMapping("/{orderId}/close")
    public ApiResponse<String> close(@PathVariable("orderId") Long orderId,
            @RequestHeader(value="X-Internal-Token",required=false) String token) {
        tokens.verify(token);
        if(orderId==null || orderId<=0) throw new IllegalArgumentException("订单编号无效");
        return ApiResponse.success(payments.closeUnpaidOrder(orderId));
    }
}
