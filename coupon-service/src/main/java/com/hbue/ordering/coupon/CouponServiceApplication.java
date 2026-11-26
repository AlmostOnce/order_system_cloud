package com.hbue.ordering.coupon;

import com.hbue.ordering.coupon.client.CouponNotificationClient;
import com.hbue.ordering.order.api.client.OrderCouponConsumeAckClient;
import com.hbue.ordering.product.api.client.WindowInternalClient;
import com.hbue.ordering.user.api.client.UserScopeInternalClient;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 优惠券服务启动入口。
 *
 * <p>本服务只扫描自身券数据访问组件，连接独立 coupon_db，并注册到 Nacos。
 * 用户领取与管理接口由 JWT 和角色权限保护；订单内部锁券/释放接口额外验证共享令牌，
 * 并作为 order-service 全局事务中的 Seata RM 分支。
 * 管理操作必须按用户数据范围授权；范围服务不可用时应拒绝操作。支付核销消费者提交券状态后，
 * 还会回调订单服务确认 Outbox，避免消息进入死信队列后订单侧失去恢复入口。</p>
 *
 * <p>同时启用定时调度，用于"到期前 24 小时提醒"：任务在本地扫描即将过期的可用券，
 * 通过内部令牌调用通知服务写入站内信。提醒与订单、工单通知分属不同通知类型和偏好分类，
 * 关闭订单或工单提醒不会影响券到期提醒。</p>
 */
@SpringBootApplication(scanBasePackages = "com.hbue.ordering")
@EnableDiscoveryClient
@EnableFeignClients(clients = {
        UserScopeInternalClient.class,
        WindowInternalClient.class,
        OrderCouponConsumeAckClient.class,
        CouponNotificationClient.class
})
@MapperScan("com.hbue.ordering.coupon.mapper")
@EnableScheduling
public class CouponServiceApplication {

    /**
     * 设置服务独立日志目录后启动 Spring 容器，避免多服务共用日志文件。
     */
    public static void main(String[] args) {
        System.getProperties().putIfAbsent("JM.LOG.PATH", java.nio.file.Path.of(
                System.getProperty("user.home"), "logs", "order-system", "coupon-service"
        ).toString());
        SpringApplication.run(CouponServiceApplication.class, args);
    }
}
