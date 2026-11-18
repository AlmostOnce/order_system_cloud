package com.hbue.ordering.coupon.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学生券及来源模板联查行；按用户券和窗口 ID 组合为对外视图。
 */
@Data
public class CouponMineRow {
    private Long userCouponId;
    private Long templateId;
    private String title;
    private BigDecimal minSpend;
    private BigDecimal discountAmount;
    private String scopeType;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private String status;
    private Long windowId;
}
