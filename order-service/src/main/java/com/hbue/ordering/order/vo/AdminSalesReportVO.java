package com.hbue.ordering.order.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端经营报表响应。
 *
 * <p>汇总值与分档口明细来自同一次数据库聚合：{@code orderCount}、{@code grossAmount}、
 * {@code paidQuantity} 是明细的合计，{@code averageOrderAmount} 由服务端按
 * “销售额 ÷ 订单数”计算并保留两位小数。统计口径与统计服务一致：以首次支付时间落区间，
 * 退款不冲减销售额与份数。</p>
 *
 * @param from              区间起点（含）
 * @param to                区间终点（不含）
 * @param windowId          请求指定的档口；按范围汇总时为 null
 * @param orderCount        区间内已支付订单总数
 * @param grossAmount       区间内应付金额合计
 * @param averageOrderAmount 客单价；无订单时为零
 * @param paidQuantity      区间内菜品原始购买份数
 * @param windows           按档口拆分的明细，按销售额降序
 * @author order-system
 * @date 2026-11-28
 */
public record AdminSalesReportVO(
        LocalDateTime from,
        LocalDateTime to,
        Long windowId,
        long orderCount,
        BigDecimal grossAmount,
        BigDecimal averageOrderAmount,
        long paidQuantity,
        List<AdminSalesWindowVO> windows
) {
}
