package com.hbue.ordering.stock.support;

/**
 * 库存流水业务类型常量。
 *
 * <p>与 {@code stock_flow.biz_type} 列取值一一对应，并与 {@code biz_ref} 一起
 * 构成幂等唯一键。扣减与回补使用不同类型，使同一订单可以「先扣后补」各记一次流水，
 * 而各自本身仍严格幂等。</p>
 *
 * @author order-system
 * @date 2026-10-19
 */
public final class StockFlowTypes {

    /**
     * 下单扣减库存。
     */
    public static final String ORDER_DEDUCT = "ORDER_DEDUCT";

    /**
     * 订单取消/退款回补库存。
     */
    public static final String ORDER_RESTORE = "ORDER_RESTORE";

    /**
     * 管理员手工调整库存。
     */
    public static final String ADMIN_ADJUST = "ADMIN_ADJUST";

    /**
     * 工具类不允许实例化。
     */
    private StockFlowTypes() {
    }
}
