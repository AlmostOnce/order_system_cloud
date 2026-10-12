package com.hbue.ordering.payment;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 支付服务启动类。
 *
 * <p>负责启动支付服务、扫描支付数据访问接口与订单服务内部客户端，并注册到 Nacos。</p>
 *
 * @author order-system
 * @date 2026-10-12
 */
@SpringBootApplication(scanBasePackages = "com.hbue.ordering")
@EnableDiscoveryClient
@EnableFeignClients
@MapperScan("com.hbue.ordering.payment.mapper")
public class PaymentServiceApplication {

    /**
     * 启动支付服务。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
