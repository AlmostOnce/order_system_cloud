package com.hbue.ordering.stock.messaging;

import com.hbue.ordering.stock.support.StockRedisScript;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

/** 收到已提交的库存变更后只失效快照，由后续请求按需回源数据库重建。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "stock.messaging.enabled", havingValue = "true")
public class StockEventConsumer {
    private final StockRedisScript stockRedisScript;
    private final StockEventConsumerMapper consumerMapper;

    public StockEventConsumer(
            StockRedisScript stockRedisScript,
            StockEventConsumerMapper consumerMapper
    ) {
        this.stockRedisScript = stockRedisScript;
        this.consumerMapper = consumerMapper;
    }

    @Bean
    public Consumer<StockChangedEvent> stockChanged() {
        return event -> {
            if (event == null || !event.valid()) {
                throw new IllegalArgumentException("库存变更事件格式不正确");
            }
            if (consumerMapper.countByEventId(event.eventId()) > 0) {
                return;
            }
            // DEL 是幂等的。若 Redis 调用失败，不落消费标记，让 Broker 按策略重试。
            stockRedisScript.invalidateSnapshot(event.windowId(), event.productId());
            consumerMapper.markConsumed(event.eventId());
        };
    }
}
