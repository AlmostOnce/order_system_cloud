package com.hbue.ordering.user.model;

import lombok.Data;

/**
 * 首屏同一一致读观察到的收藏遍历上界与发件箱最大编号；后者不是事件过滤水位。
 */
@Data
public class FavoriteRecommendationWatermarks {
    /**
     * 首屏可见的最大收藏关系编号，后续仅作为固定分页上界。
     */
    private Long favoriteWatermark;
    /**
     * 首屏可见的最大发件箱 ID，仅供诊断，不能据此跳过实时事件。
     */
    private Long observedOutboxWatermark;
}
