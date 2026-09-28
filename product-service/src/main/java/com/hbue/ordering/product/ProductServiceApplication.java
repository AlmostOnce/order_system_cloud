package com.hbue.ordering.product;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 商品服务启动类。
 *
 * <p>负责启动商品服务、扫描商品数据访问接口并注册到 Nacos。</p>
 *
 * @author order-system
 * @date 2026-09-28
 */
@SpringBootApplication(scanBasePackages = "com.hbue.ordering")
@EnableDiscoveryClient
@MapperScan("com.hbue.ordering.product.mapper")
public class ProductServiceApplication {

    /**
     * 启动商品服务。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
