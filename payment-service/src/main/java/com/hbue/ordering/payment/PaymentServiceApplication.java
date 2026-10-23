package com.hbue.ordering.payment;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 支付服务启动类。
 *
 * <p>负责启动支付服务、扫描支付数据访问接口与订单/用户服务内部客户端，并注册到 Nacos。</p>
 *
 * @author order-system
 * @date 2026-10-12
 */
@SpringBootApplication(scanBasePackages = "com.hbue.ordering")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {
        "com.hbue.ordering.payment",
        "com.hbue.ordering.user.api.client"
})
@MapperScan("com.hbue.ordering.payment.mapper")
public class PaymentServiceApplication {

    /**
     * 启动支付服务。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        // 各服务隔离 Nacos 客户端日志，避免 Windows 下共享文件滚动冲突；保留 JVM 外部配置。
        System.getProperties().putIfAbsent("JM.LOG.PATH", java.nio.file.Path.of(
                System.getProperty("user.home"), "logs", "order-system", "payment-service"
        ).toString());
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
