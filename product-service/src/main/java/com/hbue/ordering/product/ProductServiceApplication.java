package com.hbue.ordering.product;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

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
@EnableScheduling
@EnableFeignClients(basePackages = {
        "com.hbue.ordering.user.api.client",
        "com.hbue.ordering.stock.api.client",
        "com.hbue.ordering.product.api.searchclient"
})
@MapperScan("com.hbue.ordering.product.mapper")
public class ProductServiceApplication {

    /**
     * 启动商品服务。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        // 各服务隔离 Nacos 客户端日志，避免 Windows 下共享文件滚动冲突；保留 JVM 外部配置。
        System.getProperties().putIfAbsent("JM.LOG.PATH", java.nio.file.Path.of(
                System.getProperty("user.home"), "logs", "order-system", "product-service"
        ).toString());
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
