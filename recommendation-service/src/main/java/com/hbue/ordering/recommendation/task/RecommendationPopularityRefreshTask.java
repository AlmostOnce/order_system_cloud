package com.hbue.ordering.recommendation.task;

import com.hbue.ordering.common.redis.ScheduledTaskLock;
import com.hbue.ordering.recommendation.service.RecommendationPopularityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 定时同步既有销量榜；服务不可用时使用最近成功同步的榜单，不伪造空结果。
 *
 * <p>多实例部署时先用分布式租约保证同一轮只有一个实例拉取榜单，避免重复远程调用；
 * 租约取 2 分钟，远大于"拉取 Top50 + 事务整批替换"的耗时。</p>
 */
@Component
public class RecommendationPopularityRefreshTask {
    private static final Logger log = LoggerFactory.getLogger(RecommendationPopularityRefreshTask.class);
    /**
     * 稳定的任务名：所有实例必须一致，否则互斥失效。
     */
    private static final String TASK_NAME = "recommendation-popularity-refresh";
    /**
     * 租约时长：覆盖一次榜单拉取与整批替换。
     */
    private static final Duration LEASE = Duration.ofMinutes(2);
    private final RecommendationPopularityService popularity;
    /**
     * 定时任务互斥租约，避免多实例重复刷新热榜。
     */
    private final ScheduledTaskLock taskLock;

    /**
     * 注入热门读取与本地更新入口，以及定时任务互斥租约。
     */
    public RecommendationPopularityRefreshTask(RecommendationPopularityService popularity,
                                               ScheduledTaskLock taskLock) {
        this.popularity = popularity;
        this.taskLock = taskLock;
    }

    /**
     * 启动后延迟加载榜单，此后每分钟刷新一次以限制统计服务调用频率。
     */
    @Scheduled(initialDelayString = "${RECOMMENDATION_HOT_INITIAL_DELAY_MS:15000}",
            fixedDelayString = "${RECOMMENDATION_HOT_REFRESH_DELAY_MS:60000}")
    public void refresh() {
        try (ScheduledTaskLock.Lease lease = taskLock.tryAcquire(TASK_NAME, LEASE)) {
            if (lease == null) {
                // 另一个实例正在执行，或 Redis 暂不可用：跳过本轮，现有热榜保持不变。
                log.info("推荐热门榜刷新已由其它实例执行，跳过本轮");
                return;
            }
            try {
                popularity.refresh();
            } catch (RuntimeException exception) {
                log.warn("推荐热门榜刷新失败 type={}", exception.getClass().getSimpleName());
            }
        }
    }
}
