package com.hbue.ordering.order.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 账号注销阻断统计视图。
 *
 * <p>仅供用户服务内部调用，字段与用户服务 Feign 契约保持一致；只返回计数，
 * 不返回订单号、菜品金额等订单明细，避免跨服务复制业务数据。</p>
 *
 * @author order-system
 * @date 2026-11-24
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountDeletionBlockersVO {

    /**
     * 未完成订单数量。
     */
    private long unfinishedOrders;

    /**
     * 未结案退款数量。
     */
    private long unsettledRefunds;

    /**
     * 被拒绝退款数量，仅提示不阻断。
     */
    private long rejectedRefunds;
}
