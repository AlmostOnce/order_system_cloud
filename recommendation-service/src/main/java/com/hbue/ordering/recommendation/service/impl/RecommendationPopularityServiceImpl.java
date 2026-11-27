package com.hbue.ordering.recommendation.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.recommendation.client.RecommendationStatisticsClient;
import com.hbue.ordering.recommendation.service.RecommendationHotSalesWriter;
import com.hbue.ordering.recommendation.service.RecommendationPopularityService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 通过统计服务内部接口复用累计销量口径，并把有效响应写入推荐库。
 */
@Service
public class RecommendationPopularityServiceImpl implements RecommendationPopularityService {
    private final RecommendationStatisticsClient statistics;
    private final RecommendationHotSalesWriter writer;
    private final String internalToken;

    /**
     * 注入统计服务契约和本地原子榜单写入器。
     */
    public RecommendationPopularityServiceImpl(RecommendationStatisticsClient statistics,
                                               RecommendationHotSalesWriter writer,
                                               @Value("${internal.scope.token:}") String internalToken) {
        this.statistics = statistics;
        this.writer = writer;
        this.internalToken = internalToken;
    }

    /**
     * 仅标准成功响应可替换本地榜单；依赖故障由定时任务捕获并保留旧榜。
     */
    @Override
    public void refresh() {
        ApiResponse<RecommendationStatisticsClient.Ranking> response = statistics.ranking(internalToken, 50);
        if (response == null || !CommonErrorCode.SUCCESS.code().equals(response.getCode())
                || response.getData() == null || response.getData().items() == null) {
            throw new IllegalStateException("统计服务销量榜暂不可用");
        }
        writer.replace(response.getData().items());
    }
}
