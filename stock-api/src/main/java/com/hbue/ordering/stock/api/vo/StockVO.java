package com.hbue.ordering.stock.api.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 库存视图。
 *
 * <p>返回某个档口下某个菜品的库存快照，以数据库 {@code stock.available_qty} 为准，
 * Redis 中的预扣数据不作为对外事实来源。</p>
 *
 * @author order-system
 * @date 2026-10-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockVO {

    /**
     * 库存记录主键。
     */
    private Long stockId;

    /**
     * 所属档口 ID。
     */
    private Long windowId;

    /**
     * 菜品 ID。
     */
    private Long productId;

    /**
     * 总可售上限。
     */
    private Integer totalQty;

    /**
     * 当前可售数量。
     */
    private Integer availableQty;
}
