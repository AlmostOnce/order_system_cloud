package com.hbue.ordering.coupon.vo;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 学生自己的券视图；不返回别人的用户 ID 或内部资金流水字段。
 */
@Value
@Builder
public class UserCouponVO {
    Long userCouponId;
    Long templateId;
    String title;
    BigDecimal minSpend;
    BigDecimal discountAmount;
    String scopeType;
    List<Long> windowIds;
    LocalDateTime validFrom;
    LocalDateTime validUntil;
    String status;
}
