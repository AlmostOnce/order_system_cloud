package com.hbue.ordering.notification.vo;

import java.time.LocalDateTime;

/** 供客户端使用的通知，不包含接收人内部编号。 */
public record NotificationVO(String notificationId, String type, String title, String content,
        Long orderId, LocalDateTime createdAt, LocalDateTime readAt) {}
