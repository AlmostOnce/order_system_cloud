package com.hbue.ordering.recommendation.task;

import com.hbue.ordering.common.redis.ScheduledTaskLock;
import com.hbue.ordering.recommendation.service.RecommendationProductCatalogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 以有界单窗口批次修复商品刷新消息丢失，不在一次失败时清空本地目录。
 *
 * <p>多实例部署时每个实例都会触发 {@code @Scheduled}，因此先用分布式租约保证同一轮只有
 * 一个实例执行；租约取 10 分钟，需覆盖逐窗口拉取商品目录的最长耗时。任务本身对同一窗口
 * 重复刷新是幂等的（整批替换），即使租约过期后短暂并行也不会产生半新半旧目录。</p>
 */
@Component
public class RecommendationProductCatalogRefreshTask {
    private static final Logger log = LoggerFactory.getLogger(RecommendationProductCatalogRefreshTask.class);
    /**
     * 稳定的任务名：所有实例必须一致，否则互斥失效。
     */
    private static final String TASK_NAME = "recommendation-catalog-refresh";
    /**
     * 租约时长：覆盖一次全量窗口刷新，过期后由下一轮实例接管。
     */
    private static final Duration LEASE = Duration.ofMinutes(10);
    private final RecommendationProductCatalogService catalog;
    /**
     * 定时任务互斥租约，避免多实例重复对账。
     */
    private final ScheduledTaskLock taskLock;

    /**
     * 注入目录刷新入口和互斥租约；每个窗口单独提交，失败只影响其自身而保留旧快照。
     */
    public RecommendationProductCatalogRefreshTask(RecommendationProductCatalogService catalog,
                                                   ScheduledTaskLock taskLock) {
        this.catalog = catalog;
        this.taskLock = taskLock;
    }

    /**
     * 启动后等待10秒再回填，之后定期对账，避免应用启动同步等待所有商品服务响应。
     */
    @Scheduled(initialDelayString = "${RECOMMENDATION_CATALOG_INITIAL_DELAY_MS:10000}",
            fixedDelayString = "${recommendation.catalog.refresh-delay-ms:300000}")
    public void refreshAllWindows() {
        try (ScheduledTaskLock.Lease lease = taskLock.tryAcquire(TASK_NAME, LEASE)) {
            if (lease == null) {
                // 另一个实例正在执行，或 Redis 暂不可用：跳过本轮由下一轮调度重试。
                log.info("推荐目录刷新已由其它实例执行，跳过本轮");
                return;
            }
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
}
