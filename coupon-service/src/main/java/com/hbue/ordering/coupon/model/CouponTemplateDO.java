package com.hbue.ordering.coupon.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 优惠券模板在 coupon_db 中的持久化映射；仅承载数据库字段，不作为接口响应。 */
@Data
@TableName("coupon_template")
public class CouponTemplateDO {
    /** 优惠券模板主键。 */
    @TableId(value = "coupon_template_id", type = IdType.AUTO)
    private Long couponTemplateId;
    /** 学生端展示标题。 */
    @TableField("title")
    private String title;
    /** 满减门槛金额。 */
    @TableField("min_spend")
    private BigDecimal minSpend;
    /** 固定优惠金额。 */
    @TableField("discount_amount")
    private BigDecimal discountAmount;
    /** 模板最多可领取总数。 */
    @TableField("total_quantity")
    private Long totalQuantity;
    /** 已领取数量，由条件更新原子递增。 */
    @TableField("claimed_quantity")
    private Long claimedQuantity;
    /** 可领取开始时间。 */
    @TableField("claim_start_at")
    private LocalDateTime claimStartAt;
    /** 可领取截止时间。 */
    @TableField("claim_end_at")
    private LocalDateTime claimEndAt;
    /** 券可使用开始时间。 */
    @TableField("valid_from")
    private LocalDateTime validFrom;
    /** 券可使用截止时间。 */
    @TableField("valid_until")
    private LocalDateTime validUntil;
    /** 适用范围：CAMPUS 或 WINDOWS。 */
    @TableField("scope_type")
    private String scopeType;
    /** 首期固定为平台发行。 */
    @TableField("issuer_type")
    private String issuerType;
    /** 首期固定由平台预算承担。 */
    @TableField("funding_party")
    private String fundingParty;
    /** 模板生命周期：DRAFT/PUBLISHED/CLAIM_STOPPED/STOPPED。 */
    @TableField("status")
    private String status;
    /** 创建人用户 ID。 */
    @TableField("created_by")
    private Long createdBy;
    /** 创建时间。 */
    @TableField("created_at")
    private LocalDateTime createdAt;
    /** 最近更新时间。 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
