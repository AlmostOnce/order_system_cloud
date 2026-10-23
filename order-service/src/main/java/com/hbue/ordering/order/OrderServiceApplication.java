package com.hbue.ordering.order;

import com.hbue.ordering.order.config.FeignTokenRelayConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 订单微服务启动入口。
 *
 * <p>负责启动 Spring 容器、启用服务发现，并扫描订单业务与共享基础组件；
 * 同时注册 user-api、product-api、stock-api 中定义的 Feign 客户端。该类只负责应用装配，
 * 不承载订单业务逻辑或运行时业务状态。</p>
 *
 * @author order-system
 * @date 2026-09-21
 */
@SpringBootApplication(scanBasePackages = "com.hbue.ordering")
@EnableDiscoveryClient
@EnableFeignClients(
        basePackages = {
                "com.hbue.ordering.user.api.client",
                "com.hbue.ordering.product.api.client",
                "com.hbue.ordering.stock.api.client"
        },
        defaultConfiguration = FeignTokenRelayConfiguration.class
)
public class OrderServiceApplication {

    /**
     * 启动订单服务。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        // 各服务隔离 Nacos 客户端日志，避免 Windows 下共享文件滚动冲突；保留 JVM 外部配置。
        System.getProperties().putIfAbsent("JM.LOG.PATH", java.nio.file.Path.of(
                System.getProperty("user.home"), "logs", "order-system", "order-service"
        ).toString());
        // 启动 Spring Boot 应用。
        SpringApplication.run(
                OrderServiceApplication.class,
                args
        );
    }
}
