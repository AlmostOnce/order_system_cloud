package com.hbue.ordering.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 商家只提交顾客出示的凭据，不得通过查询接口获取取餐码。 */
public record OrderPickupVerifyRequest(
        @NotBlank @Pattern(regexp = "[0-9]{6}", message = "请输入六位数字取餐码") String pickupCode) {
    @Override public String toString() { return "OrderPickupVerifyRequest[redacted]"; }
}
