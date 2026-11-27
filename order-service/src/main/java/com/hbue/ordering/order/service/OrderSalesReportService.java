package com.hbue.ordering.order.service;

import com.hbue.ordering.order.vo.AdminSalesReportVO;

import java.time.LocalDateTime;

/**
 * 管理端经营报表业务服务。
 *
 * <p>只负责按操作者数据范围聚合订单库中的支付事实，不修改任何业务数据。销售额与份数
 * 统一采用“首次支付时间落入区间”的口径，退款不冲减历史数据，与统计服务的销量口径一致。
 * 报表数值全部由后端计算，前端不得根据订单明细重算。</p>
 *
 * @author order-system
 * @date 2026-11-28
 */
public interface OrderSalesReportService {

    /**
     * 汇总指定时间区间的经营数据。
     *
     * <p>指定 {@code windowId} 时只统计该档口，并要求档口在操作者范围内；未指定时按
     * 操作者范围汇总：全平台范围统计全部档口，餐厅范围展开为下属档口，档口范围只统计
     * 直接授权的档口。没有可统计范围时返回全零报表，不查询全表。</p>
     *
     * @param operatorId 当前登录操作者 ID
     * @param windowId   可选档口 ID；为 null 表示按操作者范围汇总
     * @param from       区间起点（含）
     * @param to         区间终点（不含）
     * @return 汇总值与分档口明细
     * @throws com.hbue.ordering.common.core.exception.BusinessException 参数非法、越权或依赖服务不可用时抛出
     */
    AdminSalesReportVO summarize(Long operatorId, Long windowId, LocalDateTime from, LocalDateTime to);
}
