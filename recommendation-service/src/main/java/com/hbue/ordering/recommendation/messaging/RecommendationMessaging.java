package com.hbue.ordering.recommendation.messaging;

import com.hbue.ordering.order.api.recommendation.RecommendationPurchaseSnapshotEvent;
import com.hbue.ordering.recommendation.service.RecommendationFavoriteProjectionService;
import com.hbue.ordering.recommendation.service.RecommendationProductCatalogService;
import com.hbue.ordering.recommendation.service.RecommendationPurchaseProjectionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

/**
 * RocketMQ 函数式消费者；异常向 Binder 抛出以触发有限重投和死信处理。
 */
@Configuration
public class RecommendationMessaging {
    private final RecommendationPurchaseProjectionService purchases;
    private final RecommendationFavoriteProjectionService favorites;
    private final RecommendationProductCatalogService catalog;

    /**
     * 注入三类投影处理器，消息协议与持久化幂等规则由对应服务负责。
     */
    public RecommendationMessaging(RecommendationPurchaseProjectionService purchases,
                                   RecommendationFavoriteProjectionService favorites, RecommendationProductCatalogService catalog) {
        this.purchases = purchases;
        this.favorites = favorites;
        this.catalog = catalog;
    }

    /**
     * 购买事件是订单当前完整快照，版本检查使重复/乱序重投不重复计分。
     */
    @Bean
    public Consumer<RecommendationPurchaseSnapshotEvent> orderRecommendationSnapshot() {
        return purchases::apply;
    }

    /**
     * 收藏事件含取消墓碑，投影服务按 Outbox 自增主键数值比较版本。
     */
    @Bean
    public Consumer<FavoriteChangedMessage> favoriteChanged() {
        return favorites::apply;
    }

    /**
     * 商品消息只触发读当前商品事实；乱序刷新仍读取商品服务最新快照。
     */
    @Bean
    public Consumer<ProductSearchRefreshMessage> productSearchRefresh() {
        return event -> {
            if (event == null || event.version() != 1 || event.eventId() == null || event.eventId().isBlank()
                    || event.windowId() == null || event.windowId() <= 0
                    || (event.productId() != null && event.productId() <= 0)) {
                throw new IllegalArgumentException("商品推荐刷新事件格式无效");
            }
            if (event.productId() == null) catalog.refreshWindow(event.windowId());
            else catalog.refreshProduct(event.windowId(), event.productId());
        };
    }
}
