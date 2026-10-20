package com.hbue.ordering.stock.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 库存变更单行请求。
 *
 * <p>描述同一档口下某个菜品的变更数量，作为扣减与回补请求的最小单元。
 * 该对象是跨服务契约，不是库存持久化实体。</p>
 *
 * @author order-system
 * @date 2026-10-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockItemRequest {

    /**
     * 菜品 ID。
     */
    @NotNull(message = "菜品 ID 不能为空")
    private Long productId;

    /**
     * 变更数量，必须为正整数；扣减方向由外层请求的业务类型决定。
     */
    @NotNull(message = "变更数量不能为空")
    @Min(value = 1, message = "变更数量必须大于 0")
    private Integer quantity;
}
