package com.hbue.ordering.order;

import com.hbue.ordering.order.config.FeignTokenRelayConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 订单服务启动类。
 *
 * @author order-system
 * @date 2026-09-21
 */
@SpringBootApplication(scanBasePackages = "com.hbue.ordering")
@EnableDiscoveryClient
@EnableFeignClients(
        basePackages = "com.hbue.ordering.user.api.client",
        defaultConfiguration = FeignTokenRelayConfiguration.class
)
public class OrderServiceApplication {

    /**
     * 启动订单服务。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        // 启动 Spring Boot 应用。
        SpringApplication.run(
                OrderServiceApplication.class,
                args
        );
    }
}