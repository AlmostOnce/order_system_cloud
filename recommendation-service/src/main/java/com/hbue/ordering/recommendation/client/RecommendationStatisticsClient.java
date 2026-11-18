package com.hbue.ordering.recommendation.client;

import com.hbue.ordering.common.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 推荐服务读取统计服务公开销量口径的内部契约，不自行重算或改变退款销量政策。
 */
@FeignClient(name = "statistics-service", contextId = "recommendationStatisticsClient")
public interface RecommendationStatisticsClient {
    /**
     * 获取最新累计销量榜；仅由受内部令牌保护的统计服务接口响应。
     */
    @GetMapping("/internal/statistics/rankings/sales")
    ApiResponse<Ranking> ranking(@RequestHeader("X-Internal-Token") String token,
                                 @RequestParam("limit") int limit);

    /**
     * 对齐统计服务的最小 JSON 结构，避免推荐服务依赖统计实现模块。
     */
    record Ranking(String updatedAt, List<Item> items) {
    }

    /**
     * 单条热度候选；排名与销量均由统计服务产生。
     */
    record Item(int rank, Long productId, Long windowId, String productName, long sales) {
    }
}
