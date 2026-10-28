package com.hbue.ordering.search.messaging;

import com.hbue.ordering.search.service.ProductSearchService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.support.ErrorMessage;
import java.util.function.Consumer;

/** 消费目录变更消息，以商品主库当前快照刷新一个窗口。 */
@Configuration(proxyBeanMethods = false)
public class ProductSearchRefreshConsumer {
    private final ProductSearchService search;
    public ProductSearchRefreshConsumer(ProductSearchService search) { this.search = search; }

    @Bean
    public Consumer<ProductSearchRefreshEvent> productSearchRefresh() {
        return event -> {
            if (event == null || !event.valid()) throw new IllegalArgumentException("搜索刷新事件内容无效");
            search.refreshWindow(event.windowId());
        };
    }

    @Bean
    public Consumer<ErrorMessage> productSearchRefreshRetryError() {
        return error -> { throw new IllegalStateException("搜索索引刷新失败，交由 Broker 重试", error.getPayload()); };
    }
}
