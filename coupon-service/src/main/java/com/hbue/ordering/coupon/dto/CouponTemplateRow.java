package com.hbue.ordering.coupon.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 模板与适用窗口联查的一行结果；一张窗口券会按窗口 ID 展开多行。 */
@Data
public class CouponTemplateRow {
    private Long templateId;
    private String title;
    private BigDecimal minSpend;
    private BigDecimal discountAmount;
    private Long totalQuantity;
    private Long remainingQuantity;
    private LocalDateTime claimStartAt;
    private LocalDateTime claimEndAt;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private String scopeType;
    private String status;
    private Long windowId;
}
