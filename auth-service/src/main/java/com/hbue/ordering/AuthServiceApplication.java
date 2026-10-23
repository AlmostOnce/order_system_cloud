package com.hbue.ordering;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 认证服务启动类。
 *
 * <p>负责启动认证服务、注册 Nacos，
 * 并扫描用户服务远程调用客户端。</p>
 *
 * @author order-system
 * @date 2026-09-20
 */
@SpringBootApplication(scanBasePackages = "com.hbue.ordering")
@EnableDiscoveryClient
@EnableFeignClients(
        basePackages = "com.hbue.ordering.user.api.client"
)
public class AuthServiceApplication {

    /**
     * 启动认证服务。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        // 各服务隔离 Nacos 客户端日志，避免 Windows 下共享文件滚动冲突；保留 JVM 外部配置。
        System.getProperties().putIfAbsent("JM.LOG.PATH", java.nio.file.Path.of(
                System.getProperty("user.home"), "logs", "order-system", "auth-service"
        ).toString());
        SpringApplication.run(
                AuthServiceApplication.class,
                args
        );
    }
}