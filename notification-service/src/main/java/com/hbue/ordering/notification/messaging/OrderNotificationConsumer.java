package com.hbue.ordering.notification.messaging;

import com.hbue.ordering.notification.service.OrderNotificationService;
import java.util.function.Consumer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.support.ErrorMessage;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "notification.messaging.enabled", havingValue = "true")
public class OrderNotificationConsumer {
    @Bean
    Consumer<OrderNotificationEvent> orderNotification(OrderNotificationService service) {
        return service::accept;
    }
    @Bean
    Consumer<ErrorMessage> orderNotificationRetryError() {
        return error -> { throw new IllegalStateException("订单通知消费失败，交由消息队列重试", error.getPayload()); };
    }
}
