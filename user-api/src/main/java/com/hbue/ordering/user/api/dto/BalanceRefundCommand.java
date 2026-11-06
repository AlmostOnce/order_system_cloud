package com.hbue.ordering.user.api.dto;
// 必须使用Jakarta约束参与运行时Bean Validation，不能替换成IDE同名静态分析注解。
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
/** 仅服务间使用的退款命令；原支付单号关联真实扣款，退款号稳定重试，客户端不能直接调用。 */
public record BalanceRefundCommand(
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,64}") String paymentNo,
        @NotBlank @Pattern(regexp="[a-f0-9]{32}") String refundNo,
        @NotNull @DecimalMin("0.01") @Digits(integer=8,fraction=2) BigDecimal amount) {}
