package com.hbue.ordering.user.messaging;

import com.hbue.ordering.user.mapper.FavoriteEventOutboxMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定时发送用户库发件箱；Broker 未同步确认时保留事件并退避重试。 */
@Component
@ConditionalOnProperty(name = "user.favorite-messaging.enabled", havingValue = "true")
public class FavoriteEventOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(FavoriteEventOutboxPublisher.class);
    /** 用户库事件持久化接口，作为失败恢复来源。 */
    private final FavoriteEventOutboxMapper outbox;
    /** 项目现有 Spring Cloud Stream Binder，配置为同步发送。 */
    private final StreamBridge stream;

    /** 注入发件箱和消息发送器，禁止在收藏本地事务中同步等待 Broker。 */
    public FavoriteEventOutboxPublisher(FavoriteEventOutboxMapper outbox, StreamBridge stream) {
        this.outbox = outbox;
        this.stream = stream;
    }

    /**
     * 有界扫描到期事件并发布；多实例或确认后宕机可能重发，消费者必须幂等。
     * 失败只记录事件编号和异常类型，未确认行绝不删除。
     */
    @Scheduled(fixedDelayString = "${user.favorite-messaging.publish-delay-ms:1000}")
    public void publishPending() {
        for (FavoriteEventOutboxRow row : outbox.pending()) {
            try {
                FavoriteChangedEvent event = FavoriteChangedEvent.from(row);
                if (!stream.send("favoriteChanged-out-0", MessageBuilder.withPayload(event)
                        .setHeader("KEYS", event.eventId()).build())) {
                    throw new IllegalStateException("Broker 未确认收藏事件");
                }
                outbox.markPublished(row.getId());
            } catch (Exception exception) {
                // 不输出用户编号及异常详情；记录类型可定位重试原因，避免泄露敏感数据。
                log.warn("收藏事件发送失败 id={} type={}", row.getId(), exception.getClass().getSimpleName());
                outbox.retryLater(row.getId(), exception.getClass().getSimpleName());
            }
        }
    }
}
