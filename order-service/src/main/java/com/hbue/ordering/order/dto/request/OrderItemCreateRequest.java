package com.hbue.ordering.order.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 创建订单时提交的菜品 ID 和数量。
 *
 * @author order-system
 * @date 2026-09-28
 */
@Data
public class OrderItemCreateRequest {

    /** 菜品 ID。 */
    @NotNull(message = "菜品 ID 不能为空")
    @Positive(message = "菜品 ID 必须大于 0")
    private Long productId;

    /** 购买数量。 */
    @NotNull(message = "菜品数量不能为空")
    @Positive(message = "菜品数量必须大于 0")
    private Integer quantity;
}
