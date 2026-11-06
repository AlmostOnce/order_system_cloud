package com.hbue.ordering.notification.model;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
/**
 * 订单会话表持久化对象。一笔订单唯一会话；lastSeq与摘要必须随消息提交一起更新，不能由客户端修改。
 */
@TableName("chat_conversations")
public class ChatConversationDO {
    /** 数据库内部主键，不作为对外身份标识。 */
    @TableId(type=IdType.AUTO)
    private Long id;
    /** 对外随机会话UUID，一笔订单固定复用。 */
    private String conversationUuid;
    /** 关联订单编号，由订单服务提供事实。 */
    private Long orderId;
    /** 订单号快照，用于区分同一店铺不同订单。 */
    private String orderNo;
    /** 窗口内部编号，权限必须以当前直接经营授权检查。 */
    private Long windowId;
    /** 店铺名称显示快照。 */
    private String windowName;
    /** 订单所属顾客内部编号，不向客户端暴露。 */
    private Long customerId;
    /** 会话内已提交消息的最大序号，发送事务持锁递增。 */
    private Long lastSeq;
    /** 最后一条已提交正文摘要。 */
    private String lastContent;
    /** 最近消息的服务端时间；无消息时使用创建时间排序。 */
    private LocalDateTime lastMessageAt;
    /** 服务端UTC+8创建时间，不采用客户端时间排序。 */
    private LocalDateTime createdAt;
    public Long getId() {
        return id;
    }
    public void setId(Long value) {
        id=value;
    }
    public String getConversationUuid() {
        return conversationUuid;
    }
    public void setConversationUuid(String value) {
        conversationUuid=value;
    }
    public Long getOrderId() {
        return orderId;
    }
    public void setOrderId(Long value) {
        orderId=value;
    }
    public String getOrderNo() {
        return orderNo;
    }
    public void setOrderNo(String value) {
        orderNo=value;
    }
    public Long getWindowId() {
        return windowId;
    }
    public void setWindowId(Long value) {
        windowId=value;
    }
    public String getWindowName() {
        return windowName;
    }
    public void setWindowName(String value) {
        windowName=value;
    }
    public Long getCustomerId() {
        return customerId;
    }
    public void setCustomerId(Long value) {
        customerId=value;
    }
    public Long getLastSeq() {
        return lastSeq;
    }
    public void setLastSeq(Long value) {
        lastSeq=value;
    }
    public String getLastContent() {
        return lastContent;
    }
    public void setLastContent(String value) {
        lastContent=value;
    }
    public LocalDateTime getLastMessageAt() {
        return lastMessageAt;
    }
    public void setLastMessageAt(LocalDateTime value) {
        lastMessageAt=value;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(LocalDateTime value) {
        createdAt=value;
    }
}
