package com.hbue.ordering.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 通知服务启动入口。扫描公共组件，启用定时投递，并仅注册聊天所需的远程客户端契约。
 */
@SpringBootApplication(scanBasePackages = {"com.hbue.ordering.notification", "com.hbue.ordering.common"})
@org.springframework.context.annotation.Import(com.hbue.ordering.user.api.client.fallback.UserScopeInternalClientFallbackFactory.class)
@EnableScheduling
@org.springframework.cloud.openfeign.EnableFeignClients(clients = {
    com.hbue.ordering.order.api.client.OrderChatClient.class,
    com.hbue.ordering.user.api.client.ChatUserClient.class,
    com.hbue.ordering.user.api.client.UserScopeInternalClient.class
})
public class NotificationServiceApplication {
    /**
     * 启动通知服务并由Spring管理通知、聊天、数据库和定时投递组件。
     */
    public static void main(String[] args) { SpringApplication.run(NotificationServiceApplication.class, args); }
}
