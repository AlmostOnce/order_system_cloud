package com.hbue.ordering.coupon.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 优惠券管理状态变更审计记录，确保平台活动停发操作可追溯。 */
@Data
@TableName("coupon_admin_action_record")
public class CouponAdminActionDO {
    /** 管理操作流水主键。 */
    @TableId(value = "action_id", type = IdType.AUTO)
    private Long actionId;
    /** 被操作的模板主键。 */
    @TableField("coupon_template_id")
    private Long couponTemplateId;
    /** 从已认证 JWT 取得的总经理 ID。 */
    @TableField("operator_id")
    private Long operatorId;
    /** CREATE/PUBLISH/STOP_CLAIMS/STOP_USE。 */
    @TableField("action_type")
    private String actionType;
    /** 操作前状态；创建时为空。 */
    @TableField("previous_status")
    private String previousStatus;
    /** 操作后状态。 */
    @TableField("new_status")
    private String newStatus;
    /** 操作时间。 */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
