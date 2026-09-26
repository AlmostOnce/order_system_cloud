package com.hbue.ordering.gateway;

import com.hbue.ordering.common.redis.ReactiveRedisOperationsService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;

/**
 * Gateway 服务启动类。
 */
@SpringBootApplication
@EnableDiscoveryClient
@Import(ReactiveRedisOperationsService.class)
public class GatewayServiceApplication {

    /**
     * 应用启动入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        // 启动 Spring Boot Gateway 服务。
        SpringApplication.run(GatewayServiceApplication.class, args);
    }
}