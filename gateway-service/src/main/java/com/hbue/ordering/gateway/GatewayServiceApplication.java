package com.hbue.ordering.gateway;

import com.hbue.ordering.common.redis.ReactiveRedisOperationsService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;

/**
 * Spring Cloud Gateway 服务启动入口。
 *
 * <p>负责启动响应式网关应用并启用服务发现；同时显式装配 Gateway 所需的
 * Reactive Redis 基础操作组件。路由、安全链和过滤器由各自配置类维护，
 * 本类不承载业务逻辑。</p>
 *
 * @author order-system
 * @date 2026-10-14
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
