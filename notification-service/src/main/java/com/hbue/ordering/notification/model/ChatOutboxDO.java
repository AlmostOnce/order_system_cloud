package com.hbue.ordering.notification.model;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
/**
 * 待投递聊天事件的存储投影。仅携带会话引用，不在Redis广播或投递日志中复制聊天正文。
 */
@TableName("chat_event_outbox")
public class ChatOutboxDO {
    /** 数据库内部主键，不作为对外身份标识。 */
    @TableId(type=IdType.AUTO)
    private Long id;
    /** 事务事件公开UUID，唯一键防重复记录。 */
    private String eventUuid;
    /** 对外随机会话UUID，一笔订单固定复用。 */
    private String conversationUuid;
    /** 事件已尝试投递的次数。 */
    private Long attempts;
    public Long getId() {
        return id;
    }
    public void setId(Long value) {
        id=value;
    }
    public String getEventUuid() {
        return eventUuid;
    }
    public void setEventUuid(String value) {
        eventUuid=value;
    }
    public String getConversationUuid() {
        return conversationUuid;
    }
    public void setConversationUuid(String value) {
        conversationUuid=value;
    }
    public Long getAttempts() {
        return attempts;
    }
    public void setAttempts(Long value) {
        attempts=value;
    }
}
