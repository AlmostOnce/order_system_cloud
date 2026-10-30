package com.hbue.ordering.order.vo;

/** 仅通过当前订单所属顾客的专用接口返回。 */
public record OrderPickupCodeVO(String pickupCode) {
    @Override public String toString() { return "OrderPickupCodeVO[redacted]"; }
}
