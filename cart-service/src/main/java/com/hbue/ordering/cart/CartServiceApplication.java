package com.hbue.ordering.cart;

import com.hbue.ordering.cart.config.CartProperties;
import com.hbue.ordering.order.api.client.OrderServiceClient;
import com.hbue.ordering.order.api.client.fallback.OrderServiceClientFallbackFactory;
import com.hbue.ordering.product.api.client.ProductServiceClient;
import com.hbue.ordering.product.api.client.fallback.ProductServiceClientFallbackFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

/**
 * 购物车服务启动入口。
 *
 * <p>扫描购物车业务与公共 Redis、Web 组件；当前使用 Redis 主存储，未引入数据库
 * 驱动和 MyBatis-Plus。商品核价复用 product-api，订单验证复用 order-api；
 * 不建设 MySQL 备份，不支持游客购物车。</p>
 *
 * @author order-system
 * @date 2026-10-17
 */
@SpringBootApplication(scanBasePackages = {
        "com.hbue.ordering.cart",
        "com.hbue.ordering.common.redis",
        "com.hbue.ordering.common.web"
})
@EnableDiscoveryClient
@EnableConfigurationProperties(CartProperties.class)
@EnableFeignClients(clients = {ProductServiceClient.class, OrderServiceClient.class})
@Import({ProductServiceClientFallbackFactory.class, OrderServiceClientFallbackFactory.class})
public class CartServiceApplication {

    /**
     * 启动服务并按配置向 Nacos 注册 cart-service 实例。
     *
     * @param args Spring Boot 启动参数，可覆盖当前设备的端口和中间件地址
     */
    public static void main(String[] args) {
        // 各服务隔离 Nacos 客户端日志，避免 Windows 下共享文件滚动冲突；保留 JVM 外部配置。
        System.getProperties().putIfAbsent("JM.LOG.PATH", java.nio.file.Path.of(
                System.getProperty("user.home"), "logs", "order-system", "cart-service"
        ).toString());
        // 公共组件与安全配置随 Spring 上下文一起装配。
        SpringApplication.run(CartServiceApplication.class, args);
    }
}
