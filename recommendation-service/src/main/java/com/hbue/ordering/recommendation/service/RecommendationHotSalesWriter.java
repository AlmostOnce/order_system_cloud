package com.hbue.ordering.recommendation.service;

import com.hbue.ordering.recommendation.client.RecommendationStatisticsClient;

import java.util.List;

/**
 * 更新推荐库中可重建的热门销量快照。
 */
public interface RecommendationHotSalesWriter {
    /**
     * 事务内以统计服务的完整榜单替换旧投影；异常时旧榜保持可读。
     *
     * @param items 已校验成功的最多50条菜品累计销量榜，空列表表示权威空榜
     */
    void replace(List<RecommendationStatisticsClient.Item> items);
}
