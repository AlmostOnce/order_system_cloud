package com.hbue.ordering.cart.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 添加菜品请求 DTO，仅接收菜品 ID 和增加的数量，不接受价格或用户 ID。
 *
 * @author order-system
 * @date 2026-10-17
 */
@Data
public class CartItemAddRequest {
    /** 需要加入的菜品 ID，必须属于路径中指定的窗口。 */
    @NotNull(message = "菜品 ID 不能为空")
    @Positive(message = "菜品 ID 必须大于零")
    private Long productId;
    /** 本次增加的数量；重复 POST 会重复累加，数量修改可使用 PUT 设置目标值。 */
    @NotNull(message = "增加数量不能为空")
    @Positive(message = "增加数量必须大于零")
    private Integer quantity;
}
