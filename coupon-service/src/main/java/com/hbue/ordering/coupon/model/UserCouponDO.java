package com.hbue.ordering.coupon.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学生领取券记录映射；其有效期是领取时写入的模板快照。
 */
@Data
@TableName("user_coupon")
public class UserCouponDO {
    /**
     * 学生券记录主键。
     */
    @TableId(value = "user_coupon_id", type = IdType.AUTO)
    private Long userCouponId;
    /**
     * 券来源模板主键。
     */
    @TableField("coupon_template_id")
    private Long couponTemplateId;
    /**
     * 领取学生 ID；由已认证 JWT 主体确定。
     */
    @TableField("user_id")
    private Long userId;
    /**
     * AVAILABLE/LOCKED/USED/EXPIRED/REVOKED。
     */
    @TableField("status")
    private String status;
    /**
     * 领取时固化的可用起始时间。
     */
    @TableField("valid_from")
    private LocalDateTime validFrom;
    /**
     * 领取时固化的截止时间。
     */
    @TableField("valid_until")
    private LocalDateTime validUntil;
    /**
     * 锁券时绑定的唯一订单号；释放后置空以允许后续订单重用。
     */
    @TableField("locked_order_no")
    private String lockedOrderNo;
    /**
     * 领取时间。
     */
    @TableField("claimed_at")
    private LocalDateTime claimedAt;
    /**
     * 支付确认后的核销时间。
     */
    @TableField("used_at")
    private LocalDateTime usedAt;
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
