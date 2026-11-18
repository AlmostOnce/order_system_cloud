package com.hbue.ordering.coupon.enums;

/**
 * 学生券的状态；只有支付成功事件能推进 LOCKED 到 USED。
 */
public enum UserCouponStatus {
    /**
     * 可用于新的符合条件的订单。
     */
    AVAILABLE,
    /**
     * 已被一个订单锁定，等待支付结果。
     */
    LOCKED,
    /**
     * 订单确认支付后已核销。
     */
    USED,
    /**
     * 已超过领取时固化的有效期。
     */
    EXPIRED,
    /**
     * 管理员停止使用模板时被撤销且尚未锁定的券。
     */
    REVOKED
}
