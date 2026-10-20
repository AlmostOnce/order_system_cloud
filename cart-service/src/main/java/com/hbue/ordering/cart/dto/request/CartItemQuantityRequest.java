package com.hbue.ordering.cart.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 修改购物车目标数量的请求 DTO。
 *
 * @author order-system
 * @date 2026-10-17
 */
@Data
public class CartItemQuantityRequest {
    /** 修改后的目标数量；零不是删除操作，删除应调用 DELETE 接口。 */
    @NotNull(message = "目标数量不能为空")
    @Positive(message = "目标数量必须大于零")
    private Integer quantity;
}
