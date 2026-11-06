package com.hbue.ordering.notification.messaging;

import com.hbue.ordering.notification.service.OrderNotificationService;
import java.util.function.Consumer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.support.ErrorMessage;

/**
 * 订单MQ消费绑定。业务异常交还队列重试，不能捕获后假装消费成功；启用状态由通知配置控制。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "notification.messaging.enabled", havingValue = "true")
public class OrderNotificationConsumer {
    /**
     * 把MQ输入事件交给订单通知业务服务；异常保持传播，让消息中间件决定重试。
     */
    @Bean
    Consumer<OrderNotificationEvent> orderNotification(OrderNotificationService service) {
        return service::accept;
    }
    /**
     * 注册消费错误处理器并继续抛出失败，避免框架将业务失败当作已经成功消费。
     */
    @Bean
    Consumer<ErrorMessage> orderNotificationRetryError() {
        return error -> { throw new IllegalStateException("订单通知消费失败，交由消息队列重试", error.getPayload()); };
    }
}
