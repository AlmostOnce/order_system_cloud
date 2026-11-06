package com.hbue.ordering.order.dto.request;
import jakarta.validation.constraints.*;
/** 学生只提交本人订单菜品、数量和原因，不接受金额或用户身份。 */
public record RefundApplyRequest(@NotNull @Positive Long itemId,@NotNull @Positive Integer quantity,
        @NotBlank @Size(max=500) String reason) {}
