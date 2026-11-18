package com.hbue.ordering.recommendation;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 首页推荐服务启动入口，扫描推荐域实现和项目公共安全/响应能力。
 */
@SpringBootApplication(scanBasePackages = {"com.hbue.ordering.recommendation", "com.hbue.ordering.common"})
@MapperScan("com.hbue.ordering.recommendation.mapper")
@EnableFeignClients(basePackages = {"com.hbue.ordering.product.api.recommendationclient",
        "com.hbue.ordering.recommendation.client", "com.hbue.ordering.stock.api.client"})
@EnableScheduling
public class RecommendationServiceApplication {
    /**
     * 启动推荐服务及其目录投影、HTTP、RocketMQ 与数据库适配器。
     */
    public static void main(String[] args) {
        SpringApplication.run(RecommendationServiceApplication.class, args);
    }
}
