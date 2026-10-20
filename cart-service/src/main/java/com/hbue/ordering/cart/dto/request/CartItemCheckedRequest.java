package com.hbue.ordering.cart.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 修改单项勾选状态的请求 DTO，false 是合法值，缺失字段才属于参数错误。
 *
 * @author order-system
 * @date 2026-10-17
 */
@Data
public class CartItemCheckedRequest {
    /** true 为勾选，false 为取消勾选。 */
    @NotNull(message = "勾选状态不能为空")
    private Boolean checked;
}
