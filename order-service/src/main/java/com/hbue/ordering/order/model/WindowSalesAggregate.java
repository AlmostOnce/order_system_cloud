package com.hbue.ordering.order.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 经营报表按档口聚合的中间结果。
 *
 * <p>不是数据库实体，只承接 {@code OrderMapper} 两条聚合查询的投影列：金额查询填充
 * {@code orderCount} 与 {@code grossAmount}，份数查询填充 {@code paidQuantity}，
 * 由报表服务按 {@code windowId} 合并成对外视图。使用包装类型是为了区分“未查询到”
 * 与真实的 0，避免合并时把缺失数据误当作零。</p>
 *
 * @author order-system
 * @date 2026-11-28
 */
@Data
public class WindowSalesAggregate {

    /**
     * 档口 ID。
     */
    private Long windowId;

    /**
     * 已支付订单数。
     */
    private Long orderCount;

    /**
     * 应付金额合计，退款不冲减。
     */
    private BigDecimal grossAmount;

    /**
     * 菜品原始购买份数，退款不冲减。
     */
    private Long paidQuantity;
}
