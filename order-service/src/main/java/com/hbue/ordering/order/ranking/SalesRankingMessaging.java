package com.hbue.ordering.order.ranking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.order.mapper.SalesOutboxMapper;
import com.hbue.ordering.order.service.SalesRankingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.ErrorMessage;
import java.util.function.Consumer;

/**
 * 已停用的旧订单内榜单配置，仅保留原契约方法供历史测试源码编译。
 *
 * <p>生产环境不注册本类；新 outbox 发布由 {@link SalesOutboxPublisher} 负责，榜单事件由 statistics-service 消费。</p>
 */
@Deprecated(forRemoval = false)
public class SalesRankingMessaging {
    private static final Logger log = LoggerFactory.getLogger(SalesRankingMessaging.class);
    private final SalesOutboxMapper outbox;
    private final ObjectMapper json;
    private final StreamBridge stream;
    private final SalesRankingService rankings;
    public SalesRankingMessaging(SalesOutboxMapper outbox, ObjectMapper json, StreamBridge stream, SalesRankingService rankings) {
        this.outbox = outbox; this.json = json; this.stream = stream; this.rankings = rankings;
    }

    public Consumer<OrderPaidSalesEvent> salesRankingPaid() {
        return event -> {
            if (event == null || !event.valid()) throw new IllegalArgumentException("销量事件格式不正确");
            rankings.acceptPaidOrder(event.orderId());
            // 不吞异常；Broker 重试、超过重试次数进入该消费组死信队列。
        };
    }

    public Consumer<ErrorMessage> salesRankingRetryError() {
        // 默认错误处理可能仅记录并丢弃；必须抛回 RocketMQ adapter 才会 RECONSUME_LATER。
        return error -> { throw new IllegalStateException("销量事件消费失败，交由 Broker 重试", error.getPayload()); };
    }

    public void publishPending() {
        try {
            for (var row : outbox.pending()) {
                try {
                    var event = json.readValue(row.getPayload(), OrderPaidSalesEvent.class);
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
            log.error("扫描销量事件待发送表失败", exception);
        }
    }
}
