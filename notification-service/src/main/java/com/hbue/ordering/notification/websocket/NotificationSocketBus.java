package com.hbue.ordering.notification.websocket;

import java.nio.charset.StandardCharsets;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * 通知服务多实例在线提示总线。原有用户通知与chat会话变更使用可区分的协议，避免聊天触发订单刷新。
 * Redis Pub/Sub不保存离线数据；数据库列表和客户端补拉承担恢复，不能把发布成功当成消息已读。
 */
@Component
public class NotificationSocketBus implements MessageListener {
    public static final String CHANNEL = "notification:socket:changed:v1";
    private final com.hbue.ordering.common.redis.RedisOperationsService redis;
    private final NotificationSocketHandler sockets;
    /** 注入公共Redis发布组件与本实例Socket处理器；总线只传播可补查提示，不保存聊天正文。 */
    public NotificationSocketBus(com.hbue.ordering.common.redis.RedisOperationsService redis, NotificationSocketHandler sockets) { this.redis = redis; this.sockets = sockets; }
    /**
     * 向Redis发布可补查的在线提示。发送不是可靠消息消费确认，离线和丢失提示通过数据库列表恢复。
     */
    public void publish(long userId) { redis.publish(CHANNEL, Long.toString(userId)); }
    /**
     * 发布带chat前缀的会话变更引用，不广播聊天正文；接收实例再次检查实际连接用户的当前权限。
     */
    public void publishChat(String uuid) { redis.publish(CHANNEL, "chat:" + uuid); }
    /**
     * 区分聊天变更引用和原有用户通知编号，分发到对应Socket方法；损坏的数字提示直接忽略。
     */
    @Override public void onMessage(Message message, byte[] pattern) {
        try {
            String value = new String(message.getBody(), StandardCharsets.UTF_8);
            if (value.startsWith("chat:")) { sockets.notifyChat(value.substring(5)); return; }
            long userId = Long.parseLong(value);
            if (userId > 0) sockets.notifyUser(userId);
        } catch (NumberFormatException ignored) { }
    }
}
