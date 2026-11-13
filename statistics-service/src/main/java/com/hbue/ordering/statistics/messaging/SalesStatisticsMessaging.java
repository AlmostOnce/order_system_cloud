package com.hbue.ordering.statistics.messaging;

import com.hbue.ordering.statistics.service.SalesStatisticsService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.support.ErrorMessage;

import java.util.function.Consumer;

/**
 * 统计销量的 RocketMQ 消费配置。
 *
 * <p>数据库或 Redis 失败时必须把异常交回 Binder；Consumer 最大本地重试设为1，由 RocketMQ 负责延迟重投和死信。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "statistics.messaging.enabled", havingValue = "true")
public class SalesStatisticsMessaging {
    private final SalesStatisticsService statistics;

    /** 消费函数只编排统计服务；Broker 的重试规则由 application-statistics-mq.yml 定义。 */
    public SalesStatisticsMessaging(SalesStatisticsService statistics) {
        this.statistics = statistics;
    }

    /** 将订单支付事件交给幂等投影；异常向外传播以触发 Broker 重投。 */
    @Bean
    public Consumer<SalesPaidEvent> salesStatisticsPaid() {
        return statistics::consume;
    }

    /**
     * 继续接收历史 V1 Topic 中已经写入 Broker 的 ID-only 事件；已迁移事实会幂等跳过，迁移后的遗漏事件回读订单快照。
     *
     * <p>切换生产者 Topic 后仍消费旧队列，避免已发布但尚未消费的旧消息永久滞留。</p>
     */
    @Bean
    public Consumer<SalesPaidEvent> legacySalesStatisticsPaid() {
        return statistics::consume;
    }

    /** 显式重抛消费错误，避免默认错误通道仅记录日志后误确认消息。 */
    @Bean
    public Consumer<ErrorMessage> salesStatisticsRetryError() {
        return error -> { throw new IllegalStateException("销量读模型消费失败，交由 RocketMQ 重试", error.getPayload()); };
    }
}
