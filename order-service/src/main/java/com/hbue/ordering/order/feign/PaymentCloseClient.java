package com.hbue.ordering.order.feign;
import com.hbue.ordering.common.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
@FeignClient(name="payment-service",contextId="orderPaymentClose")
public interface PaymentCloseClient {
    @PostMapping("/internal/payments/orders/{orderId}/close")
    ApiResponse<String> close(@PathVariable("orderId") Long orderId,@RequestHeader("X-Internal-Token") String token);
}
