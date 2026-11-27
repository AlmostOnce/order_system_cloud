package com.hbue.ordering.stock.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.function.Consumer;

/**
 * 可靠发布已提交库存变更事件；Broker 故障时保留 outbox 并退避重试。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "stock.messaging.enabled", havingValue = "true")
public class StockEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(StockEventPublisher.class);

    /**
     * 单条事件的发送租约（秒）。多实例部署时同一行会被同时扫到，只有把 {@code next_attempt_at}
     * 原子推进到未来的实例才发送；该值需大于一次同步发送的最长耗时。
     */
    private static final int LEASE_SECONDS = 30;

    private final StockEventOutboxMapper outbox;
    private final StreamBridge stream;

    public StockEventPublisher(StockEventOutboxMapper outbox, StreamBridge stream) {
        this.outbox = outbox;
        this.stream = stream;
    }

    @Scheduled(fixedDelayString = "${stock.messaging.publish-delay-ms:1000}")
    public void publishPending() {
        for (StockEventOutboxRow row : outbox.pending()) {
            try {
                // 先原子占租约：并发实例中只有一个能把 next_attempt_at 推进到未来并得到 1，
                // 其余实例拿到 0 后跳过，保证同一库存事件不会被重复外发。
                if (outbox.lease(row.getEventId(), LEASE_SECONDS) != 1) continue;
                StockChangedEvent event = StockChangedEvent.of(row);
                if (!event.valid()) {
                    throw new IllegalArgumentException("库存事件数据不完整");
                }
                boolean confirmed = stream.send(
                        "stockChanged-out-0",
                        MessageBuilder.withPayload(event)
                                .setHeader("KEYS", event.eventId())
                                .build()
                );
                if (!confirmed) {
                    throw new IllegalStateException("Broker 未确认接收库存事件");
                }
                outbox.markPublished(row.getEventId());
            } catch (Exception exception) {
                log.warn("库存事件发布失败，保留待重试记录 eventId={}", row.getEventId(), exception);
                outbox.retryLater(row.getEventId(), exception.getClass().getSimpleName());
            }
        }
    }

    @Bean
    public Consumer<ErrorMessage> stockChangedRetryError() {
        return error -> {
            throw new IllegalStateException("库存变更事件消费失败，交由 Broker 重试", error.getPayload());
        };
    }
}
