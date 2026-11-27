package com.hbue.ordering.order.vo;

import java.math.BigDecimal;

/**
 * 经营报表中单个档口的聚合行。
 *
 * <p>所有数值均由订单服务数据库聚合后返回，前端只负责格式化展示，不得自行按订单
 * 重算金额或客单价。销售额与份数采用“首次支付”口径，退款不冲减历史数据。</p>
 *
 * @param windowId    档口 ID
 * @param windowName  档口名称；商品服务暂不可用时为 null，前端回落展示编号
 * @param orderCount  区间内已支付订单数
 * @param grossAmount 区间内应付金额合计
 * @param paidQuantity 区间内菜品原始购买份数
 * @author order-system
 * @date 2026-11-28
 */
public record AdminSalesWindowVO(
        Long windowId,
        String windowName,
        long orderCount,
        BigDecimal grossAmount,
        long paidQuantity
) {
}
