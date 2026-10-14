package com.hbue.ordering.order.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 创建订单时提交的一行商品 ID 和购买数量。
 *
 * <p>该请求不包含商品名称或价格，这些数据由订单服务通过商品服务核价取得，
 * 并以快照形式写入订单明细。</p>
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
