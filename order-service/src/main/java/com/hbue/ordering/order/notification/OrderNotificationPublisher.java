package com.hbue.ordering.order.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.order.mapper.OrderNotificationOutboxMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "notification.order-events.enabled", havingValue = "true")
public class OrderNotificationPublisher {
    private static final Logger log = LoggerFactory.getLogger(OrderNotificationPublisher.class);
    private final OrderNotificationOutboxMapper mapper;
    private final ObjectMapper json;
    private final StreamBridge stream;
    public OrderNotificationPublisher(OrderNotificationOutboxMapper mapper, ObjectMapper json, StreamBridge stream) {
        this.mapper = mapper; this.json = json; this.stream = stream;
    }
    @Scheduled(fixedDelayString = "${notification.order-events.publish-delay-ms:1000}")
    public void publish() {
        try {
            for (var row : mapper.pending()) {
                try {
                    var event = json.readValue(row.getPayload(), OrderNotificationEvent.class);
                    if (!row.getEventId().equals(event.eventId()) || !row.getOrderId().equals(event.orderId()))
                        throw new IllegalArgumentException("订单通知事件数据不一致");
                    if (!stream.send("orderNotification-out-0", MessageBuilder.withPayload(event).setHeader("KEYS", event.eventId()).build()))
                        throw new IllegalStateException("消息队列未确认接收");
                    mapper.markPublished(event.eventId());
                } catch (Exception error) {
                    mapper.retryLater(row.getEventId(), error.getClass().getSimpleName());
                    log.warn("订单通知事件发送失败，等待重试，eventId={}", row.getEventId());
                }
            }
        } catch (RuntimeException error) { log.warn("订单通知待发送记录扫描失败", error); }
    }
}
