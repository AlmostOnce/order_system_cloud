package com.hbue.ordering.coupon.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 每个订单唯一的锁定/核销/释放流水映射，保存实际优惠成本快照。
 */
@Data
@TableName("coupon_use_record")
public class CouponUseRecordDO {
    /**
     * 使用流水主键。
     */
    @TableId(value = "coupon_use_record_id", type = IdType.AUTO)
    private Long couponUseRecordId;
    /**
     * 被使用的学生券 ID。
     */
    @TableField("user_coupon_id")
    private Long userCouponId;
    /**
     * 券模板 ID。
     */
    @TableField("coupon_template_id")
    private Long couponTemplateId;
    /**
     * 领取该券的用户 ID。
     */
    @TableField("user_id")
    private Long userId;
    /**
     * 锁券关联订单号，也是订单级幂等键。
     */
    @TableField("order_no")
    private String orderNo;
    /**
     * LOCKED/USED/RELEASED。
     */
    @TableField("status")
    private String status;
    /**
     * 锁定时确定的优惠金额。
     */
    @TableField("discount_amount")
    private BigDecimal discountAmount;
    /**
     * 首期由平台预算承担的成本金额，与优惠金额一致。
     */
    @TableField("platform_funded_amount")
    private BigDecimal platformFundedAmount;
    /**
     * 首期固定为零，保留字段供后续结算核算。
     */
    @TableField("window_funded_amount")
    private BigDecimal windowFundedAmount;
    /**
     * 首次锁定时间。
     */
    @TableField("locked_at")
    private LocalDateTime lockedAt;
    /**
     * 核销时间。
     */
    @TableField("used_at")
    private LocalDateTime usedAt;
    /**
     * 释放时间。
     */
    @TableField("released_at")
    private LocalDateTime releasedAt;
    /**
     * 记录创建时间。
     */
    @TableField("created_at")
    private LocalDateTime createdAt;
    /**
     * 最近更新时间。
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
