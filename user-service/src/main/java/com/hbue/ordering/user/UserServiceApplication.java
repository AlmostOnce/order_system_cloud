package com.hbue.ordering.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 用户服务启动类。
 *
 * <p>负责启动用户微服务，并将服务注册到 Nacos。</p>
 *
 * @author order-system
 * @date 2026-09-19
 */
@SpringBootApplication(scanBasePackages = "com.hbue.ordering")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.hbue.ordering.product.api.client")
public class UserServiceApplication {

    /**
     * 启动用户服务。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        // 各服务隔离 Nacos 客户端日志，避免 Windows 下共享文件滚动冲突；保留 JVM 外部配置。
        System.getProperties().putIfAbsent("JM.LOG.PATH", java.nio.file.Path.of(
                System.getProperty("user.home"), "logs", "order-system", "user-service"
        ).toString());
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
