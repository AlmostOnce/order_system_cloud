package com.hbue.ordering.support;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * 帮助与反馈服务启动类。
 *
 * <p>服务承载学生反馈工单的提交、查询、补充与关闭，以及管理端的受理、回复、
 * 内部备注和关闭。学生身份来自 JWT，管理端身份同时受方法级角色注解和
 * 数据范围服务约束；跨服务依赖只通过内部接口或透传用户凭据完成。</p>
 *
 * @author order-system
 * @date 2026-11-23
 */
@SpringBootApplication(scanBasePackages = {"com.hbue.ordering.support", "com.hbue.ordering.common"})
@EnableFeignClients(basePackages = "com.hbue.ordering.support.client")
@EnableMethodSecurity
public class SupportServiceApplication {

    /**
     * 启动反馈服务。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        // 每个服务的 MyBatis-Plus 日志按服务名分开落盘，便于定位工单 SQL 问题。
        System.getProperties().putIfAbsent("JM.LOG.PATH", java.nio.file.Path.of(
                System.getProperty("user.home"), "logs", "order-system", "support-service").toString());
        SpringApplication.run(SupportServiceApplication.class, args);
    }
}
