package com.hbue.ordering.notification.messaging;
import com.hbue.ordering.notification.mapper.ChatOutboxMapper;
import com.hbue.ordering.notification.websocket.NotificationSocketBus;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
/**
 * 提交后的聊天事件发布任务。消息写入事务只保存outbox，本任务每秒处理最多50条到期事件。
 * 多实例可能重复发布同一提示，客户端补查和消息UUID去重负责吸收重复；发布不代表客户端收到。
 */
@Component
@ConditionalOnProperty(name="notification.chat.enabled",havingValue="true")
public class ChatOutboxPublisher {
    private final ChatOutboxMapper outbox;
    private final NotificationSocketBus bus;
    /** 注入事件Mapper和跨实例提示总线；定时任务只在聊天开关开启时注册。 */
    public ChatOutboxPublisher(ChatOutboxMapper outbox,NotificationSocketBus bus){
        this.outbox=outbox;
        this.bus=bus;
    }
    /**
     * 每轮读取最多50条到期事件并发布会话变更引用；成功标记已发布，失败延期重试，聊天正文仍保留在数据库。
     */
    @Scheduled(fixedDelay=1000)
    public void publish(){
        // 查询只取已提交的到期记录；同一事件重复发布不会重复插入聊天正文。
        for(var event:outbox.pending()){
            try{
                bus.publishChat(event.getConversationUuid());
                outbox.delivered(event.getId());
            }
            catch(RuntimeException e){
                outbox.retry(event.getId());
            }
        }
    }
}
