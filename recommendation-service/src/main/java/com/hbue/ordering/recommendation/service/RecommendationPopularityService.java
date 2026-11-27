package com.hbue.ordering.recommendation.service;

/**
 * 从统计服务拉取并校验当前热门销量榜。
 */
public interface RecommendationPopularityService {
    /**
     * 读取并校验统计服务标准成功响应，再以事务更新本地榜单；失败向调用方抛出并保留旧榜。
     */
    void refresh();
}
