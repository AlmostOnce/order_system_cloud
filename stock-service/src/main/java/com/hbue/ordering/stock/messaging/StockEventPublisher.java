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

/** 可靠发布已提交库存变更事件；Broker 故障时保留 outbox 并退避重试。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "stock.messaging.enabled", havingValue = "true")
public class StockEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(StockEventPublisher.class);

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
