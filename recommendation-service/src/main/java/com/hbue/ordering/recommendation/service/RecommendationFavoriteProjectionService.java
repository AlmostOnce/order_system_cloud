package com.hbue.ordering.recommendation.service;

import com.hbue.ordering.recommendation.messaging.FavoriteChangedMessage;

/** 将实时收藏事件和权威收藏历史快照应用到推荐服务当前态投影。 */
public interface RecommendationFavoriteProjectionService {
    /**
     * 应用含Outbox数值版本的实时收藏变更或取消墓碑。
     *
     * @param event 已由受信任消息Topic反序列化的收藏变化，不含客户端可指定的版本
     */
    void apply(FavoriteChangedMessage event);

    /**
     * 应用用户服务内部游标导出的当前收藏；历史无事件的关系允许版本零。
     *
     * @param userId 用户服务内部编号
     * @param targetType 目标类型，仅允许PRODUCT或WINDOW
     * @param targetId 菜品或窗口主键
     * @param sourceVersion 本关系当前可见的最大Outbox编号，历史关系可为0
     * @param active 历史导出只返回当前存在关系，必须为true
     * @param changedAt 源端关系创建时间，用于诊断，不用于版本排序
     */
    void applySnapshot(Long userId, String targetType, Long targetId, Long sourceVersion,
            Boolean active, java.time.LocalDateTime changedAt);
}
