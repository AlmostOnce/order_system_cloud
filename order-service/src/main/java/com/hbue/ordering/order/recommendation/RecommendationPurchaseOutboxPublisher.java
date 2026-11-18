package com.hbue.ordering.order.recommendation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.order.api.recommendation.RecommendationPurchaseSnapshotEvent;
import com.hbue.ordering.order.mapper.RecommendationPurchaseOutboxMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 推荐专用Outbox派发器，复用现有RocketMQ同步确认和数据库退避方式，不改销量Topic。
 * 多实例可能重复发送同一事件，消息携带稳定键和源版本；接收端必须幂等，不承诺恰好一次投递。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "recommendation.messaging.enabled", havingValue = "true")
public class RecommendationPurchaseOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(RecommendationPurchaseOutboxPublisher.class);
    /**
     * 本地可靠队列与项目统一JSON编码器。
     */
    private final RecommendationPurchaseOutboxMapper outbox;
    private final ObjectMapper json;
    /**
     * 只发布不可变Outbox正文，不在派发阶段重新读取并改写历史事件。
     */
    private final StreamBridge stream;

    /**
     * 注入持久任务与消息桥；开关只控制派发，不跳过源事务记录。
     */
    public RecommendationPurchaseOutboxPublisher(RecommendationPurchaseOutboxMapper outbox,
                                                 ObjectMapper json, StreamBridge stream) {
        this.outbox = outbox;
        this.json = json;
        this.stream = stream;
    }

    /**
     * 有界扫描和校验后同步发送；未获Broker确认则保留任务退避，不伪报完成。
     */
    @Scheduled(fixedDelayString = "${recommendation.messaging.publish-delay-ms:1000}")
    public void publishPending() {
        try {
            for (var row : outbox.pending()) {
                try {
                    var event = json.readValue(row.getPayload(), RecommendationPurchaseSnapshotEvent.class);
                    if (!event.valid() || !event.eventId().equals(row.getEventId())
                            || !event.orderId().equals(row.getOrderId()) || !event.orderVersion().equals(row.getOrderVersion())) {
                        throw new IllegalArgumentException("推荐Outbox正文身份不一致");
                    }
                    for (var item : event.items()) {
                        OrderSpecificationSnapshot.validate(item.selectionKey(), item.specificationSelectionSnapshot(), true);
                    }
                    boolean confirmed = stream.send("recommendationPurchase-out-0", MessageBuilder.withPayload(event)
                            .setHeader("KEYS", event.eventId()).build());
                    if (!confirmed) throw new IllegalStateException("Broker未确认推荐事件");
                    outbox.markPublished(event.eventId());
                } catch (Exception exception) {
                    // 不回显异常对象或消息正文，避免异常消息中可能包含的购买、规格及凭据进入日志。
                    log.warn("推荐事件派发待重试，eventId={}，error={}", row.getEventId(), exception.getClass().getSimpleName());
                    outbox.retryLater(row.getEventId(), exception.getClass().getSimpleName());
                }
            }
        } catch (Exception exception) {
            log.error("扫描推荐购买Outbox失败，error={}", exception.getClass().getSimpleName());
        }
    }
}
