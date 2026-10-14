package com.hbue.ordering.user.api.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 余额扣款结果视图。
 *
 * <p>这是 user-service 返回给 payment-service 的账务结果契约，包含扣款业务引用、金额、扣款后余额以及幂等重放标记。
 * 只有收到明确的成功响应后，payment-service 才能继续推进支付单和订单状态；该对象不是余额流水持久化实体。</p>
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
