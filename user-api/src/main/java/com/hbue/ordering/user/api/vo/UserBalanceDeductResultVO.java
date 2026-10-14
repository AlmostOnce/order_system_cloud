package com.hbue.ordering.user.api.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 余额扣款结果视图。
 *
 * <p>返回扣款业务单据、金额、扣款后余额以及本次是否为幂等重放，
 * 供 payment-service 判断是否继续推进支付单与订单状态。</p>
 *
 * @author order-system
 * @date 2026-10-14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBalanceDeductResultVO {

    /**
     * 用户 ID。
     */
    private Long userId;

    /**
     * 业务单据引用（支付单号）。
     */
    private String bizRef;

    /**
     * 本次扣款金额。
     */
    private BigDecimal amount;

    /**
     * 扣款后余额。
     */
    private BigDecimal balanceAfter;

    /**
     * 是否命中幂等重放（同一业务单据已记过账）。
     */
    private Boolean idempotent;
}
