package com.hbue.ordering.user.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

import java.math.BigDecimal;

/** 本系统模拟充值，无第三方扣款；支付密码仅用于本次身份验证。 */
@Data
public class RechargeRequest {
    @NotNull
    @DecimalMin("0.01")
    @DecimalMax("10000.00")
    @Digits(integer = 5, fraction = 2)
    private BigDecimal amount;

    @NotBlank
    @Size(min = 6, max = 20)
    @ToString.Exclude
    private String payPassword;
}
