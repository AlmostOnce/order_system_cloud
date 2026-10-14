package com.hbue.ordering.user.api.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 余额扣款命令。
 *
 * <p>由 payment-service 在余额渠道下单时提交给 user-service，
 * 账务（改余额 + 写流水）在 user-service 同库同事务完成。</p>
 *
 * <p>{@code bizType} 与 {@code bizRef} 指向业务单据，与 {@code change_type}
 * 一起构成 {@code balance_records} 的幂等唯一键，重复提交不会重复扣款。</p>
 *
 * <p>支付密码只在本次请求的内存中短暂传递，禁止写入日志。</p>
 *
 * @author order-system
 * @date 2026-10-14
 */
@Data
public class UserBalanceDeductCommand {

    /**
     * 业务类型，如 PAYMENT。
     */
    private String bizType;

    /**
     * 业务单据引用，如支付单号 paymentNo。
     */
    private String bizRef;

    /**
     * 扣款金额，必须为正数。
     */
    private BigDecimal amount;

    /**
     * 用户支付密码明文（传输层由内部安全链路保护，用后即弃）。
     */
    private String payPassword;

    /**
     * 备注，用于流水排查。
     */
    private String remark;
}
