package com.hbue.ordering.notification.config;

import com.hbue.ordering.notification.websocket.NotificationSocketBus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Redis在线提示监听配置。同一通知服务的多个实例订阅相同主题，分别处理本机持有的连接。
 */
@Configuration
public class NotificationSocketBusConfig {
    /**
     * 创建Redis主题监听容器，使通知服务每个实例接收在线提示并处理自己的连接。
     */
    @Bean
    RedisMessageListenerContainer notificationSocketListener(RedisConnectionFactory factory, NotificationSocketBus listener) {
        var container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener(listener, new ChannelTopic(NotificationSocketBus.CHANNEL));
        return container;
    }
}
