package com.hbue.ordering.notification.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 系统通知表持久化对象。内部主键仅供存储排序，公开通知UUID供接口引用，来源事件键保证MQ消费幂等。
 */
@TableName("notifications")
public class NotificationDO {
    /** 数据库自增主键，只用于内部持久化，不作为对外通知标识。 */
    /** 对外通知UUID，与数据库内部主键分离。 */
    @TableId(type = IdType.AUTO) private Long id;
    private String notificationId;
    /** 系统通知接收人内部编号，所有本人查询均绑定此范围。 */
    private Long recipientId;
    /** 来源业务事件标识，配合接收人保证消费幂等。 */
    private String eventId;
    /** 系统通知类型，独立于聊天消息类型。 */
    private String notificationType;
    /** 通知标题快照。 */
    private String title;
    /** 由订单事件组装的系统通知正文快照，不与顾客商家聊天正文混用。 */
    private String content;
    /** 关联订单编号，由订单服务提供事实。 */
    private Long orderId;
    /** 服务端UTC+8创建时间，不采用客户端时间排序。 */
    private LocalDateTime createdAt;
    /** 首次阅读时间，空值表示未读。 */
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
