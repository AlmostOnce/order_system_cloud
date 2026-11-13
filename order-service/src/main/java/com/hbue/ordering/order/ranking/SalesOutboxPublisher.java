package com.hbue.ordering.order.ranking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.order.mapper.SalesOutboxMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 订单服务销量 outbox 发布器。
 *
 * <p>订单服务只负责在本地事务保存事实并可靠发布，不再消费或维护排行榜；Broker 确认后才标记 outbox 已发布，
 * 未确认或异常均按数据库退避计划重试。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "ranking.messaging.enabled", havingValue = "true")
public class SalesOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(SalesOutboxPublisher.class);
    private final SalesOutboxMapper outbox;
    private final ObjectMapper json;
    private final StreamBridge stream;

    /** 注入订单本地 outbox、JSON 编码器和 RocketMQ 同步发送桥。 */
    public SalesOutboxPublisher(SalesOutboxMapper outbox, ObjectMapper json, StreamBridge stream) {
        this.outbox = outbox;
        this.json = json;
        this.stream = stream;
    }

    /** 扫描有界待发送事件；Broker 未确认时保留 outbox 行并让数据库计算下一次重试时间。 */
    @Scheduled(fixedDelayString = "${ranking.messaging.publish-delay-ms:1000}")
    public void publishPending() {
        try {
            for (var row : outbox.pending()) {
                try {
                    OrderPaidSalesEvent event = json.readValue(row.getPayload(), OrderPaidSalesEvent.class);
                    if (!event.valid() || !event.eventId().equals(row.getEventId()) || !event.orderId().equals(row.getOrderId())) {
                        throw new IllegalArgumentException("本地销量事件不完整");
                    }
                    boolean confirmed = stream.send("salesPaid-out-0", MessageBuilder.withPayload(event)
                            .setHeader("KEYS", event.eventId()).build());
                    if (!confirmed) throw new IllegalStateException("Broker 未确认接收销量事件");
                    outbox.markPublished(row.getEventId());
                } catch (Exception exception) {
                    log.warn("销量事件发布待重试，eventId={}", row.getEventId(), exception);
                    outbox.retryLater(row.getEventId(), exception.getClass().getSimpleName());
                }
            }
        } catch (Exception exception) {
            log.error("扫描销量事件 outbox 失败", exception);
        }
    }
}
