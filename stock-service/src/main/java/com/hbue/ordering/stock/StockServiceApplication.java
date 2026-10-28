package com.hbue.ordering.stock;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 库存服务启动类。
 *
 * <p>负责启动库存服务、扫描库存与库存流水的数据访问接口并注册到 Nacos。
 * 本服务只对外提供 {@code /internal/**} 内部接口，供 order-service 在
 * 下单链路（Seata AT 全局事务分支）中扣减库存、在取消退款链路中回补库存。</p>
 *
 * <p>开启定时任务，用于比对 Redis 快照与数据库可售数量的漂移并修正。</p>
 *
 * @author order-system
 * @date 2026-10-19
 */
@SpringBootApplication(scanBasePackages = "com.hbue.ordering")
@EnableDiscoveryClient
@EnableScheduling
@MapperScan({
        "com.hbue.ordering.stock.mapper",
        "com.hbue.ordering.stock.messaging"
})
public class StockServiceApplication {

    /**
     * 启动库存服务。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        // 各服务隔离 Nacos 客户端日志，避免 Windows 下共享文件滚动冲突；保留 JVM 外部配置。
        System.getProperties().putIfAbsent("JM.LOG.PATH", java.nio.file.Path.of(
                System.getProperty("user.home"), "logs", "order-system", "stock-service"
        ).toString());
        SpringApplication.run(StockServiceApplication.class, args);
    }
}
