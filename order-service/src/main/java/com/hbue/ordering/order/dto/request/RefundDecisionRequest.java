package com.hbue.ordering.order.dto.request;
import jakarta.validation.constraints.*;
/** 商家/经理审批命令，拒绝或回库存均记录原因；完成订单服务端强制禁止回库存。 */
public record RefundDecisionRequest(@NotNull Boolean approve,@NotNull Boolean restock,
        @NotBlank @Size(max=500) String reason) {}
