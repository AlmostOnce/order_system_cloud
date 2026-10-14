package com.hbue.ordering.user.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 余额流水持久化对象。
 *
 * <p>用于映射 balance_records 表。账务（改 {@code users.balance} 与写本表）
 * 在 user-service 同库同事务完成，供对账与审计使用。</p>
 *
 * <p>唯一键 {@code (user_id, biz_type, biz_ref, change_type)} 让同一业务单据的
 * 同一动作只能记一次账，重复回调或重试直接冲突失败，保证记账幂等。</p>
 *
 * @author order-system
 * @date 2026-10-14
 */
@Data
@TableName("balance_records")
public class BalanceRecordDO {

    /**
     * 流水记录 ID。
     */
    @TableId(value = "record_id", type = IdType.AUTO)
    private Long recordId;

    /**
     * 用户 ID。
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 变动类型：RECHARGE 入账 / PAY 消费 / REFUND 退回。
     */
    @TableField("change_type")
    private String changeType;

    /**
     * 本次业务变动金额；入账或扣款方向由 {@code change_type} 标识，扣款流程以正数记录扣款额。
     */
    @TableField("amount")
    private BigDecimal amount;

    /**
     * 变动后余额。
     */
    @TableField("balance_after")
    private BigDecimal balanceAfter;

    /**
     * 业务类型，如 PAYMENT。
     */
    @TableField("biz_type")
    private String bizType;

    /**
     * 业务单据引用，如支付单号。
     */
    @TableField("biz_ref")
    private String bizRef;

    /**
     * 备注。
     */
    @TableField("remark")
    private String remark;

    /**
     * 创建时间。
     */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
