package com.hbue.ordering.notification.config;

import com.hbue.ordering.notification.websocket.NotificationSocketBus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class NotificationSocketBusConfig {
    @Bean
    RedisMessageListenerContainer notificationSocketListener(RedisConnectionFactory factory, NotificationSocketBus listener) {
        var container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener(listener, new ChannelTopic(NotificationSocketBus.CHANNEL));
        return container;
    }
}
