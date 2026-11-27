package com.hbue.ordering.user.client.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 订单服务返回的账号注销阻断统计。
 *
 * <p>字段与 order-service 内部接口 JSON 契约一一对应；只返回计数，不返回订单或
 * 退款明细，避免把订单数据复制到用户服务。任一计数大于 0 都会阻断注销。</p>
 *
 * @author order-system
 * @date 2026-11-24
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderDeletionBlockersVO {

    /**
     * 未完成订单数量：待支付、已支付、制作中、待取货。
     */
    private long unfinishedOrders;

    /**
     * 未结案退款数量：待处理、申诉中、已同意、处理中。
     */
    private long unsettledRefunds;

    /**
     * 历史上被拒绝的退款数量，仅用于提示，不阻断注销。
     */
    private long rejectedRefunds;
}
