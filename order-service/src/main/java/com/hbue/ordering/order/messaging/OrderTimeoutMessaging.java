package com.hbue.ordering.order.messaging;

import com.hbue.ordering.order.dto.OrderTimeoutEvent;
import com.hbue.ordering.order.service.OrderTimeoutOutboxService;
import com.hbue.ordering.order.service.OrderTimeoutService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.function.Consumer;

/**
 * 仅扫描待发送事件；不扫描订单。失败消费交由Broker重试及死信。
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "order.payment-timeout.enabled", havingValue = "true")
public class OrderTimeoutMessaging {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OrderTimeoutMessaging.class);
    /**
     * 单条超时事件的发送租约（秒）。多实例同一轮扫描到同一行时，只有成功推进
     * {@code next_attempt_at} 的实例负责发送；租约需大于一次同步发送的最长耗时。
     */
    private static final int LEASE_SECONDS = 30;
    private final OrderTimeoutOutboxService outbox;
    private final OrderTimeoutService timeouts;
    private final StreamBridge stream;

    public OrderTimeoutMessaging(OrderTimeoutOutboxService outbox, OrderTimeoutService timeouts, StreamBridge stream) {
        this.outbox = outbox;
        this.timeouts = timeouts;
        this.stream = stream;
    }

    @Bean
    public Consumer<OrderTimeoutEvent> orderPaymentTimeout() {
        return event -> {
            if (event == null || !event.valid()) throw new IllegalArgumentException("无效超时消息");
            long remaining = timeouts.remainingMillis(event.orderId());
            if (remaining > 0) send(event, remaining);
            else timeouts.expire(event.orderId());
        };
    }

    @Bean
    public Consumer<ErrorMessage> orderTimeoutRetryError() {
        return error -> {
            throw new IllegalStateException("订单超时处理失败，交由Broker重试", error.getPayload());
        };
    }

    @Bean
    public Consumer<OrderTimeoutEvent> orderTimeoutDeadLetter() {
        return event -> {
            if (event == null || !event.valid()) {
                log.error("ORDER_TIMEOUT_DEAD_LETTER：死信事件格式异常，需人工核查");
                return;
            }
            outbox.deadLetter(event.eventId());
            log.error("ORDER_TIMEOUT_DEAD_LETTER：超时订单需人工补偿，orderId={}，eventId={}", event.orderId(), event.eventId());
        };
    }

    @Scheduled(fixedDelayString = "${order.payment-timeout.publish-delay-ms:1000}")
    public void publish() {
        try {
            for (var event : outbox.pending()) {
                try {
                    if (!event.valid()) throw new IllegalArgumentException("无效待发送事件");
                    // 先原子占租约：并发实例同一轮读到同一行时只有一方能领取，其余跳过本次发送；
                    // 领取失败不进入失败退避，避免把另一实例正在发送的记录误标为失败。
                    if (!outbox.lease(event.eventId(), LEASE_SECONDS)) continue;
                    send(event, Math.max(0, event.expiresAt() - System.currentTimeMillis()));
                    outbox.sent(event.eventId());
                } catch (Exception error) {
                    outbox.failed(event.eventId(), error.getClass().getSimpleName());
                    log.error("ORDER_TIMEOUT_PUBLISH_RETRY：超时事件待重试，eventId={}", event.eventId(), error);
                }
            }
        } catch (RuntimeException error) {
            log.error("ORDER_TIMEOUT_OUTBOX_UNAVAILABLE：无法读取超时事件", error);
        }
    }

    private void send(OrderTimeoutEvent event, long remaining) {
        var message = MessageBuilder.withPayload(event).setHeader("KEYS", event.eventId());
        int level = delayLevel(remaining);
        if (level > 0) message.setHeader("DELAY", level);
        if (!stream.send("orderTimeout-out-0", message.build())) throw new IllegalStateException("Broker未确认接收");
    }

    /**
     * 选择不超过剩余时间的最大延迟；不足1秒用1秒，消费者按数据库期限复核。
     *
     * <p>返回值直接作为 RocketMQ 的 {@code DELAY} 头（延迟级别），因此数组下标必须与
     * Broker 的 {@code messageDelayLevel} 严格对齐：RocketMQ 默认只有 18 档
     * （1s/5s/10s/30s/1m/2m/…/1h/2h），数组长度只能是 18，多出的档位会映射成越界级别
     * 导致 Broker 拒收并反复走 outbox 重试。剩余时间超过 2 小时时钳制到最高档，消息到期后
     * 消费端仍会用 {@code remainingMillis} 复核并重新投递，所以选择较短的档位不会提前关单。</p>
     *
     * @param millis 距订单支付期限的剩余毫秒数
     * @return 1~18 的延迟级别；已到期返回 0 表示不设置延迟
     */
    static int delayLevel(long millis) {
        if (millis <= 0) return 0;
        // 与 RocketMQ 默认 18 档 messageDelayLevel 一一对应，禁止再追加自定义档位。
        long[] seconds = {1, 5, 10, 30, 60, 120, 180, 240, 300, 360, 420, 480, 540, 600, 1200, 1800, 3600, 7200};
        int level = 1;
        long best = 0;
        for (int i = 0; i < seconds.length; i++)
            if (seconds[i] * 1000 <= millis && seconds[i] > best) {
                best = seconds[i];
                level = i + 1;
            }
        return level;
    }
}
