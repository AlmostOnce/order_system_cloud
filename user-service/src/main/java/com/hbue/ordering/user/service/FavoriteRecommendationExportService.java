package com.hbue.ordering.user.service;

import com.hbue.ordering.user.vo.FavoriteRecommendationExportPageVO;

/** 为推荐服务提供受保护、有界的当前收藏关系历史导出。 */
public interface FavoriteRecommendationExportService {
    /**
     * 返回固定收藏主键上界的一页当前关系及逐关系Outbox版本。
     *
     * @param afterFavoriteId 上一页末收藏主键，首屏为0
     * @param watermark 首屏为null；后续必须原样使用响应中的favoriteId上界，而非Outbox全局最大编号
     * @param limit 单页收藏数，范围为1至100
     * @return 当前存在关系的有界页面，不将跨页缺失解释为取消
     */
    FavoriteRecommendationExportPageVO list(long afterFavoriteId, Long watermark, int limit);
}
