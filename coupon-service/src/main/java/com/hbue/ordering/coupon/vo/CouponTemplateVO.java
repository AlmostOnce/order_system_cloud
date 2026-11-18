package com.hbue.ordering.coupon.vo;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 学生可领取或总经理管理的模板视图，窗口 ID 只用于说明适用范围。
 */
@Value
@Builder
public class CouponTemplateVO {
    Long templateId;
    String title;
    BigDecimal minSpend;
    BigDecimal discountAmount;
    String scopeType;
    List<Long> windowIds;
    LocalDateTime claimStartAt;
    LocalDateTime claimEndAt;
    LocalDateTime validFrom;
    LocalDateTime validUntil;
    Long totalQuantity;
    Long remainingQuantity;
    String status;
}
