package com.hbue.ordering.coupon.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 即将到期的用户券扫描行。
 *
 * <p>只取提醒所需的最小字段：接收人、券记录编号、券标题与截止时间。
 * 标题来自模板联查，用于让提醒正文能直接说明是哪一张券，而不是笼统的"您有券要过期"。
 *
 * @author order-system
 * @date 2026-11-25
 */
@Data
public class CouponExpiryReminderRow {

    /**
     * 用户券记录编号，同时作为提醒事件标识与业务跳转标识的来源。
     */
    private Long userCouponId;

    /**
     * 持券学生内部用户编号，即通知接收人。
     */
    private Long userId;

    /**
     * 券标题快照，取自当前模板标题。
     */
    private String title;

    /**
     * 券可用截止时间，提醒正文需要展示到分钟。
     */
    private LocalDateTime validUntil;
}
