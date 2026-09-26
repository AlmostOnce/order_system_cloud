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
        SpringApplication.run(
                AuthServiceApplication.class,
                args
        );
    }
}