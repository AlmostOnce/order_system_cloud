package com.hbue.ordering.product.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.hbue.ordering.product.mapper.ProductSearchOutboxMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定时可靠发布商品搜索刷新事件；数据库 outbox 是 Broker 故障时的重试来源。 */
@Component
@ConditionalOnProperty(name = "product.search-messaging.enabled", havingValue = "true")
public class ProductSearchOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(ProductSearchOutboxPublisher.class);
    private final ProductSearchOutboxMapper mapper;
    private final StreamBridge stream;

    public ProductSearchOutboxPublisher(ProductSearchOutboxMapper mapper, StreamBridge stream) {
        this.mapper = mapper;
        this.stream = stream;
    }

    @Scheduled(fixedDelayString = "${product.search-messaging.publish-delay-ms:1000}")
    public void publishPending() {
        for (ProductSearchOutboxRow row : mapper.pending()) {
            try {
                ProductSearchRefreshEvent event = new ProductSearchRefreshEvent(1, row.getEventId(), row.getWindowId());
                if (!stream.send("productSearchRefresh-out-0", MessageBuilder.withPayload(event)
                        .setHeader("KEYS", event.eventId()).build())) throw new IllegalStateException("Broker 未确认接收搜索刷新事件");
                mapper.markPublished(row.getEventId());
            } catch (Exception exception) {
                log.warn("搜索刷新事件发布失败 eventId={}", row.getEventId(), exception);
                mapper.retryLater(row.getEventId(), exception.getClass().getSimpleName());
            }
        }
    }

    @Scheduled(cron = "0 20 3 * * *")
    public void purgeOldPublished() {
        mapper.deleteOldPublished();
    }
}
