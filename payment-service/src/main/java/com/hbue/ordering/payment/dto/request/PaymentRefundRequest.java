package com.hbue.ordering.payment.dto.request;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
/** 订单服务请求的退款号与金额；原支付单只能由支付服务按订单读取。 */
public record PaymentRefundRequest(@NotBlank @Pattern(regexp="[a-f0-9]{32}") String refundNo,
        @NotNull @DecimalMin("0.01") @Digits(integer=8,fraction=2) BigDecimal amount) {}
