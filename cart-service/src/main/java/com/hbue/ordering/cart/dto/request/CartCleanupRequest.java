package com.hbue.ordering.cart.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 订单成功后的清理请求，不包含用户 ID 和价格。只接受本次订单的完整购买快照。
 *
 * @param windowId 本次订单所属窗口
 * @param items 下单前保存的商品、数量和版本，最多 100 项
 */
public record CartCleanupRequest(@NotNull(message = "窗口 ID 不能为空") @Positive(message = "窗口 ID 必须大于零") Long windowId,
        @NotEmpty(message = "提交明细不能为空") @Size(max = 100, message = "提交明细不能超过 100 项")
        List<@NotNull(message = "商品明细不能为空") @Valid CartSubmittedItemRequest> items) {
}
