package com.hbue.ordering.cart.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 下单前保存的单项购物车快照。重试清理必须沿用原始版本，不能刷新版本后再次删除。
 *
 * @param productId 下单商品 ID
 * @param quantity 实际提交订单的数量
 * @param revision 下单前 GET /cart 返回的原始修改版本
 */
public record CartSubmittedItemRequest(
        @NotNull(message = "商品 ID 不能为空") @Positive(message = "商品 ID 必须大于零") Long productId,
        @NotNull(message = "购买数量不能为空") @Positive(message = "购买数量必须大于零") Integer quantity,
        @NotBlank(message = "原始版本不能为空") @Size(max = 128, message = "原始版本不能超过 128 个字符") String revision) {
}
