package com.hbue.ordering.recommendation.task;

import com.hbue.ordering.recommendation.service.RecommendationPopularityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时同步既有销量榜；服务不可用时使用最近成功同步的榜单，不伪造空结果。
 */
@Component
public class RecommendationPopularityRefreshTask {
    private static final Logger log = LoggerFactory.getLogger(RecommendationPopularityRefreshTask.class);
    private final RecommendationPopularityService popularity;

    /**
     * 注入热门读取与本地更新入口。
     */
    public RecommendationPopularityRefreshTask(RecommendationPopularityService popularity) {
        this.popularity = popularity;
    }

    /**
     * 启动后延迟加载榜单，此后每分钟刷新一次以限制统计服务调用频率。
     */
    @Scheduled(initialDelayString = "${RECOMMENDATION_HOT_INITIAL_DELAY_MS:15000}",
            fixedDelayString = "${RECOMMENDATION_HOT_REFRESH_DELAY_MS:60000}")
    public void refresh() {
        try {
            popularity.refresh();
        } catch (RuntimeException exception) {
            log.warn("推荐热门榜刷新失败 type={}", exception.getClass().getSimpleName());
        }
    }
}
