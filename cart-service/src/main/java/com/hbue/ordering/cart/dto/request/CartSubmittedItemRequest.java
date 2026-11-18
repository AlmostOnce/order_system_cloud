package com.hbue.ordering.cart.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 下单前保存的单项购物车快照。重试清理必须沿用原始版本，不能刷新版本后再次删除。
 *
 * @param productId    下单商品 ID
 * @param quantity     实际提交订单的数量
 * @param revision     下单前 GET /cart 返回的原始修改版本
 * @param selectionKey 规格组合键；旧客户端缺省时兼容为 base
 */
public record CartSubmittedItemRequest(
        @NotNull(message = "商品 ID 不能为空") @Positive(message = "商品 ID 必须大于零") Long productId,
        @NotNull(message = "购买数量不能为空") @Positive(message = "购买数量必须大于零") Integer quantity,
        @NotBlank(message = "原始版本不能为空") @Size(max = 128, message = "原始版本不能超过 128 个字符") String revision,
        @Size(max = 210, message = "规格键不能超过 210 个字符") String selectionKey) {

    /**
     * 兼容旧客户端与已有调用点；没有传规格键的快照只能指向 base 行。
     */
    public CartSubmittedItemRequest(Long productId, Integer quantity, String revision) {
        this(productId, quantity, revision, "base");
    }

    /**
     * JSON 中缺失或显式为 null 的规格键都按旧版无规格购物车处理。
     */
    public CartSubmittedItemRequest {
        if (selectionKey == null || selectionKey.isBlank()) {
            selectionKey = "base";
        }
    }
}
