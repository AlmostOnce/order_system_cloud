package com.hbue.ordering.recommendation.task;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.order.api.recommendation.RecommendationPurchaseSnapshotEvent;
import com.hbue.ordering.order.api.recommendation.RecommendationPurchaseSnapshotPageVO;
import com.hbue.ordering.recommendation.client.RecommendationFavoriteSourceClient;
import com.hbue.ordering.recommendation.client.RecommendationPurchaseSourceClient;
import com.hbue.ordering.recommendation.mapper.RecommendationSourceCursorMapper;
import com.hbue.ordering.recommendation.model.RecommendationSourceCursorDO;
import com.hbue.ordering.recommendation.service.RecommendationFavoriteProjectionService;
import com.hbue.ordering.recommendation.service.RecommendationPurchaseProjectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 将订单和收藏权威库的既有事实分批补入推荐投影。
 * 每页完整投影成功后才推进本地游标；中途宕机重读当前页，依靠源版本幂等避免重复偏好。
 */
@Component
public class RecommendationSourceBackfillTask {
    private static final Logger log = LoggerFactory.getLogger(RecommendationSourceBackfillTask.class);
    private static final String ORDER_SOURCE = "purchase-orders";
    private static final String FAVORITE_SOURCE = "favorite-relations";
    private static final int PAGE_SIZE = 100;
    /**
     * 每次定时最多读取固定数量页面，避免历史积压长期占用服务线程。
     */
    private static final int MAX_PAGES_PER_RUN = 10;

    private final RecommendationSourceCursorMapper cursors;
    private final RecommendationPurchaseSourceClient purchaseSource;
    private final RecommendationFavoriteSourceClient favoriteSource;
    private final RecommendationPurchaseProjectionService purchases;
    private final RecommendationFavoriteProjectionService favorites;
    private final String internalToken;
    private final String orderInternalToken;

    /**
     * 两类内部服务使用项目现有凭据；订单令牌与普通内部作用域令牌分开。
     */
    public RecommendationSourceBackfillTask(RecommendationSourceCursorMapper cursors,
                                            RecommendationPurchaseSourceClient purchaseSource, RecommendationFavoriteSourceClient favoriteSource,
                                            RecommendationPurchaseProjectionService purchases, RecommendationFavoriteProjectionService favorites,
                                            @Value("${internal.scope.token:}") String internalToken,
                                            @Value("${internal.order.token:}") String orderInternalToken) {
        this.cursors = cursors;
        this.purchaseSource = purchaseSource;
        this.favoriteSource = favoriteSource;
        this.purchases = purchases;
        this.favorites = favorites;
        this.internalToken = internalToken;
        this.orderInternalToken = orderInternalToken;
    }

    /**
     * 定时续跑两类历史快照；单一来源失败不会阻断另一类，后续周期按持久断点重试。
     */
    @Scheduled(initialDelayString = "${recommendation.backfill.initial-delay-ms:15000}",
            fixedDelayString = "${recommendation.backfill.delay-ms:60000}")
    public void backfill() {
        readOrderPages();
        readFavoritePages();
    }

    /**
     * 订单全量快照版本覆盖规则使历史页与并发实时支付/退款消息可以任意先后到达。
     */
    private void readOrderPages() {
        try {
            cursors.ensureExists(ORDER_SOURCE);
            for (int pageNumber = 0; pageNumber < MAX_PAGES_PER_RUN; pageNumber++) {
                RecommendationSourceCursorDO cursor = requiredCursor(ORDER_SOURCE);
                if (Boolean.TRUE.equals(cursor.getCompleted())) return;
                long after = cursor.getCursorValue() == null ? 0 : cursor.getCursorValue();
                ApiResponse<RecommendationPurchaseSnapshotPageVO> response = purchaseSource.page(
                        orderInternalToken, after, PAGE_SIZE);
                RecommendationPurchaseSnapshotPageVO page = requireData(response, "订单");
                List<RecommendationPurchaseSnapshotEvent> items = page.items();
                if (items == null || items.size() > PAGE_SIZE) throw new IllegalStateException("订单历史页超出上限");
                long lastOrderId = after;
                for (RecommendationPurchaseSnapshotEvent event : items) {
                    if (event == null || event.orderId() == null || event.orderId() <= lastOrderId) {
                        throw new IllegalStateException("订单历史页游标顺序无效");
                    }
                    purchases.apply(event);
                    lastOrderId = event.orderId();
                }
                if (page.hasMore() && (items.isEmpty() || page.nextAfterOrderId() == null
                        || !page.nextAfterOrderId().equals(lastOrderId))) {
                    throw new IllegalStateException("订单历史下一页游标缺失或不匹配");
                }
                if (!page.hasMore() && page.nextAfterOrderId() != null) {
                    throw new IllegalStateException("订单末页不应返回下一页游标");
                }
                advance(ORDER_SOURCE, lastOrderId, null, !page.hasMore());
                if (!page.hasMore()) return;
            }
        } catch (Exception exception) {
            // 内部订单/用户编号及关键词不写日志；异常类型足以判断远程不可用或数据契约失败。
            log.warn("推荐订单历史回填暂未完成 type={}", exception.getClass().getSimpleName());
        }
    }

    /**
     * 收藏回填的 watermark 只限制首屏 favoriteId 上界；逐关系版本与实时事件比较，
     * 绝不以 outbox 全局最大 ID 丢弃较晚提交的事件，也不将跨页缺失行当成删除。
     */
    private void readFavoritePages() {
        try {
            cursors.ensureExists(FAVORITE_SOURCE);
            for (int pageNumber = 0; pageNumber < MAX_PAGES_PER_RUN; pageNumber++) {
                RecommendationSourceCursorDO cursor = requiredCursor(FAVORITE_SOURCE);
                if (Boolean.TRUE.equals(cursor.getCompleted())) return;
                long after = cursor.getCursorValue() == null ? 0 : cursor.getCursorValue();
                Long watermark = cursor.getUpperBound();
                ApiResponse<RecommendationFavoriteSourceClient.RecommendationFavoriteSnapshotPage> response =
                        favoriteSource.page(internalToken, after, watermark, PAGE_SIZE);
                RecommendationFavoriteSourceClient.RecommendationFavoriteSnapshotPage page =
                        requireData(response, "收藏");
                if (page.items() == null || page.items().size() > PAGE_SIZE || page.watermark() < 0
                        || watermark != null && !watermark.equals(page.watermark())) {
                    throw new IllegalStateException("收藏历史页上界或数据格式无效");
                }
                long lastFavoriteId = after;
                for (RecommendationFavoriteSourceClient.RecommendationFavoriteSnapshotPage.Item item : page.items()) {
                    if (item == null || item.favoriteId() == null || item.favoriteId() <= lastFavoriteId
                            || item.favoriteId() > page.watermark()) {
                        throw new IllegalStateException("收藏历史页游标顺序无效");
                    }
                    favorites.applySnapshot(item.userId(), item.targetType(), item.targetId(),
                            item.sourceVersion(), item.active(), item.createdAt());
                    lastFavoriteId = item.favoriteId();
                }
                if (page.hasMore() && (page.items().isEmpty() || page.nextCursor() == null
                        || !page.nextCursor().equals(lastFavoriteId))) {
                    throw new IllegalStateException("收藏历史下一页游标缺失或不匹配");
                }
                if (!page.hasMore() && page.nextCursor() != null) {
                    throw new IllegalStateException("收藏末页不应返回下一页游标");
                }
                advance(FAVORITE_SOURCE, lastFavoriteId, page.watermark(), !page.hasMore());
                if (!page.hasMore()) return;
            }
        } catch (Exception exception) {
            log.warn("推荐收藏历史回填暂未完成 type={}", exception.getClass().getSimpleName());
        }
    }

    /**
     * 验证服务响应及 ApiResponse 业务码；不把空或失败响应误当成空历史并完成回填。
     */
    private <T> T requireData(ApiResponse<T> response, String source) {
        if (response == null || !CommonErrorCode.SUCCESS.code().equals(response.getCode())
                || response.getData() == null) {
            throw new IllegalStateException(source + "历史源未返回成功数据");
        }
        return response.getData();
    }

    /**
     * 断点行必须已成功创建；缺失时停止推进避免静默从头开始。
     */
    private RecommendationSourceCursorDO requiredCursor(String name) {
        RecommendationSourceCursorDO cursor = cursors.select(name);
        if (cursor == null) throw new IllegalStateException("推荐历史回填断点不存在");
        return cursor;
    }

    /**
     * 更新影响行数为零表示结构异常，不得继续向后跳过尚未落库的页面。
     */
    private void advance(String source, long value, Long upperBound, boolean completed) {
        if (cursors.advance(source, value, upperBound, completed) != 1) {
            throw new IllegalStateException("推荐历史回填断点推进失败");
        }
    }
}
