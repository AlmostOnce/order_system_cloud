package com.hbue.ordering.statistics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 统计服务启动入口。
 *
 * <p>本服务只扫描统计模块与公共能力，订单读取通过受令牌保护的 order-api 契约完成；
 * 定时任务仅用于从本地 MySQL 汇总修复可重建的 Redis 排名缓存。</p>
 */
@SpringBootApplication(scanBasePackages = {"com.hbue.ordering.statistics", "com.hbue.ordering.common"})
@EnableFeignClients(clients = com.hbue.ordering.order.api.client.OrderSalesInternalClient.class)
@EnableScheduling
public class StatisticsServiceApplication {
    /** 启动统计服务并由 Spring 管理 HTTP、MQ、数据库和 Redis 投影组件。 */
    public static void main(String[] args) {
        SpringApplication.run(StatisticsServiceApplication.class, args);
    }
}
