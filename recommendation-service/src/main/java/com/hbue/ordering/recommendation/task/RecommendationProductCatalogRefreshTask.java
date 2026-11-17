package com.hbue.ordering.recommendation.task;

import com.hbue.ordering.recommendation.service.RecommendationProductCatalogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 以有界单窗口批次修复商品刷新消息丢失，不在一次失败时清空本地目录。 */
@Component
public class RecommendationProductCatalogRefreshTask {
    private static final Logger log = LoggerFactory.getLogger(RecommendationProductCatalogRefreshTask.class);
    private final RecommendationProductCatalogService catalog;

    /** 注入目录刷新入口；每个窗口单独提交，失败只影响其自身而保留旧快照。 */
    public RecommendationProductCatalogRefreshTask(RecommendationProductCatalogService catalog) {
        this.catalog = catalog;
    }

    /** 启动后等待10秒再回填，之后定期对账，避免应用启动同步等待所有商品服务响应。 */
    @Scheduled(initialDelayString = "${RECOMMENDATION_CATALOG_INITIAL_DELAY_MS:10000}",
            fixedDelayString = "${recommendation.catalog.refresh-delay-ms:300000}")
    public void refreshAllWindows() {
        try {
            var activeWindowIds = catalog.windowIds();
            for (Long windowId : activeWindowIds) {
                try {
                    catalog.refreshWindow(windowId);
                } catch (RuntimeException exception) {
                    // 不记录菜品文案或用户数据；下轮对账重试该窗口，保留当前已知目录。
                    log.warn("推荐目录刷新失败 windowId={} type={}", windowId,
                            exception.getClass().getSimpleName());
                }
            }
            // 商品服务完整窗口清单成功后，已停用窗口的旧菜品不能继续作为探索候选。
            catalog.markWindowsMissing(activeWindowIds);
        } catch (RuntimeException exception) {
            log.warn("推荐目录窗口列表读取失败 type={}", exception.getClass().getSimpleName());
        }
    }
}
