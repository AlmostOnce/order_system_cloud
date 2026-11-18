package com.hbue.ordering.recommendation.service;

import com.hbue.ordering.recommendation.vo.RecommendationHomeVO;

/**
 * 为当前已认证顾客编排首页推荐读取、排序及当前商品/库存复核。
 */
public interface RecommendationQueryService {
    /**
     * 查询本人在可选餐厅范围内的有界菜品推荐列表，并由商品/库存服务复核当前展示事实。
     *
     * @param userId       认证层从已验签顾客会话解析出的内部编号，不来自HTTP参数
     * @param restaurantId 可选餐厅筛选，null表示全部当前有效餐厅
     * @param limit        返回数量，服务边界限制1至10
     * @return 包含本人推荐模式及当前有效菜品卡片的响应数据，不包含个人行为明细
     */
    RecommendationHomeVO recommend(Long userId, Long restaurantId, int limit);
}
