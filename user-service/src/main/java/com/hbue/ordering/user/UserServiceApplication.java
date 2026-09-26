package com.hbue.ordering.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 用户服务启动类。
 *
 * <p>负责启动用户微服务，并将服务注册到 Nacos。</p>
 *
 * @author order-system
 * @date 2026-09-19
 */
@SpringBootApplication(scanBasePackages = {
        "com.hbue.ordering.user",
        "com.hbue.ordering.common.web.handler"
})
@EnableDiscoveryClient
public class UserServiceApplication {

    /**
     * 启动用户服务。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
