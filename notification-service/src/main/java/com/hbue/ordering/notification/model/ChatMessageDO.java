package com.hbue.ordering.notification.model;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
/**
 * 文字消息表持久化对象。保存真实发送人、服务端顺序和幂等键；对外只通过ChatMessageVO投影公开字段。
 */
@TableName("chat_messages")
public class ChatMessageDO {
    /** 数据库内部主键，不作为对外身份标识。 */
    @TableId(type=IdType.AUTO)
    private Long id;
    /** 对外消息UUID，客户端用于去重。 */
    private String messageUuid;
    /** 消息或阅读状态所属会话内部引用。 */
    private Long conversationId;
    /** 会话内服务端递增序号，用于分页与阅读水位。 */
    private Long seq;
    /** 实际发送人的内部编号，不能由请求体指定。 */
    private Long senderId;
    /** 发送人公开UUID，避免暴露内部用户主键。 */
    private String senderUuid;
    /** 实际发送人昵称快照。 */
    private String senderName;
    /** 发送方业务身份快照：顾客或商家。 */
    private String senderSide;
    /** 客户端一次发送意图的幂等UUID，失败重试必须复用。 */
    private String clientMessageId;
    /** 纯文本消息正文，发送前校验长度且不写普通日志。 */
    private String content;
    /** 服务端UTC+8创建时间，不采用客户端时间排序。 */
    private LocalDateTime createdAt;
    public Long getId() {
        return id;
    }
    public void setId(Long value) {
        id=value;
    }
    public String getMessageUuid() {
        return messageUuid;
    }
    public void setMessageUuid(String value) {
        messageUuid=value;
    }
    public Long getConversationId() {
        return conversationId;
    }
    public void setConversationId(Long value) {
        conversationId=value;
    }
    public Long getSeq() {
        return seq;
    }
    public void setSeq(Long value) {
        seq=value;
    }
    public Long getSenderId() {
        return senderId;
    }
    public void setSenderId(Long value) {
        senderId=value;
    }
    public String getSenderUuid() {
        return senderUuid;
    }
    public void setSenderUuid(String value) {
        senderUuid=value;
    }
    public String getSenderName() {
        return senderName;
    }
    public void setSenderName(String value) {
        senderName=value;
    }
    public String getSenderSide() {
        return senderSide;
    }
    public void setSenderSide(String value) {
        senderSide=value;
    }
    public String getClientMessageId() {
        return clientMessageId;
    }
    public void setClientMessageId(String value) {
        clientMessageId=value;
    }
    public String getContent() {
        return content;
    }
    public void setContent(String value) {
        content=value;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(LocalDateTime value) {
        createdAt=value;
    }
}
