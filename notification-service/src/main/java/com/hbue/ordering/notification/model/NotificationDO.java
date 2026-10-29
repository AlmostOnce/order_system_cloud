package com.hbue.ordering.notification.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("notifications")
public class NotificationDO {
    @TableId(type = IdType.AUTO) private Long id;
    private String notificationId;
    private Long recipientId;
    private String eventId;
    private String notificationType;
    private String title;
    private String content;
    private Long orderId;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public String getNotificationId() { return notificationId; } public void setNotificationId(String v) { notificationId = v; }
    public Long getRecipientId() { return recipientId; } public void setRecipientId(Long v) { recipientId = v; }
    public String getEventId() { return eventId; } public void setEventId(String v) { eventId = v; }
    public String getNotificationType() { return notificationType; } public void setNotificationType(String v) { notificationType = v; }
    public String getTitle() { return title; } public void setTitle(String v) { title = v; }
    public String getContent() { return content; } public void setContent(String v) { content = v; }
    public Long getOrderId() { return orderId; } public void setOrderId(Long v) { orderId = v; }
    public LocalDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(LocalDateTime v) { createdAt = v; }
    public LocalDateTime getReadAt() { return readAt; } public void setReadAt(LocalDateTime v) { readAt = v; }
}
