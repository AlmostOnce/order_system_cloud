package com.hbue.ordering.recommendation.task;

import com.hbue.ordering.recommendation.service.RecommendationSearchIntentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时执行短期搜索意图过期清理；失败保留数据并交下一周期重试。
 */
@Component
public class RecommendationSearchIntentExpiryTask {
    private static final Logger log = LoggerFactory.getLogger(RecommendationSearchIntentExpiryTask.class);
    private final RecommendationSearchIntentService searches;

    /**
     * 清理任务只依赖服务契约，不访问持久层。
     */
    public RecommendationSearchIntentExpiryTask(RecommendationSearchIntentService searches) {
        this.searches = searches;
    }

    /**
     * 每小时清理已超过14天的搜索词和首屏结果；异常由下一次周期继续重试。
     */
    @Scheduled(cron = "0 20 * * * *")
    public void purgeExpired() {
        try {
            searches.purgeExpired();
        } catch (RuntimeException exception) {
            log.warn("推荐搜索意图过期清理失败 type={}", exception.getClass().getSimpleName());
        }
    }
}
