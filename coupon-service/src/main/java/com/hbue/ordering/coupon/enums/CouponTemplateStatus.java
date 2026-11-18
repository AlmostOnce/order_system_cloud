package com.hbue.ordering.coupon.enums;

/**
 * 券模板的有限状态；停止领取不会作废已领券，停止使用会撤销未锁定券。
 */
public enum CouponTemplateStatus {
    /**
     * 尚未对学生开放。
     */
    DRAFT,
    /**
     * 可按时间与库存规则领取、使用。
     */
    PUBLISHED,
    /**
     * 停止新领取，但已领取且有效的券仍可使用。
     */
    CLAIM_STOPPED,
    /**
     * 停止新领取及新锁券，未锁定的已领券被撤销。
     */
    STOPPED
}
