package com.hbue.ordering.user.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 推荐服务内部收藏回填分页响应；不同页面为各自一致读，不构成跨请求完整快照。
 *
 * @param watermark               首屏收藏编号上界，后续页必须原样传回
 * @param observedOutboxWatermark 仅首屏返回可见最大事件编号，供观测，不作为过滤阈值
 * @param items                   当前页真实存在的收藏关系和逐关系版本
 * @param nextCursor              下一页的 afterFavoriteId；无下一页时为空
 * @param hasMore                 是否检测到额外一条记录
 */
public record FavoriteRecommendationExportPageVO(long watermark, Long observedOutboxWatermark,
                                                 List<Item> items, Long nextCursor, boolean hasMore) {
    /**
     * 单条收藏快照；sourceVersion 按该关系的可见发件箱编号数值比较。
     *
     * @param favoriteId    收藏关系编号，仅作游标
     * @param userId        内部用户编号，仅对通过内部凭据验证的服务返回
     * @param targetType    PRODUCT 菜品或 WINDOW 店铺
     * @param targetId      菜品或店铺编号
     * @param active        本接口只返回当前收藏关系，因此固定为 true
     * @param createdAt     当前收藏关系创建时间
     * @param sourceVersion 本关系最新可见事件编号，旧收藏尚无事件时为零
     */
    public record Item(Long favoriteId, Long userId, String targetType, Long targetId,
                       boolean active, LocalDateTime createdAt, Long sourceVersion) {
    }
}
