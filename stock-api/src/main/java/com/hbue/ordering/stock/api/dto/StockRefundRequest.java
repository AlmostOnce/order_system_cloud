package com.hbue.ordering.stock.api.dto;
import jakarta.validation.constraints.*;
/** 一笔退款只对应一道订单菜品；orderNo关联原扣减，refundNo用于独立幂等，不能复用整单回补号。 */
public record StockRefundRequest(@NotBlank @Size(max=64) String orderNo,
        @NotBlank @Pattern(regexp="[a-f0-9]{32}") String refundNo,
        @NotNull @Positive Long windowId,@NotNull @Positive Long productId,@NotNull @Positive Integer quantity) {}
