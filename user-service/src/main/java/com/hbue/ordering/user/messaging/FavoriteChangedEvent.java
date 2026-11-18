package com.hbue.ordering.user.messaging;

import java.time.LocalDateTime;

/**
 * 发送给可信内部消费者的收藏状态快照；取消收藏由 active=false 保留墓碑语义。
 *
 * @param eventId    格式 favorite:<发件箱自增编号>；消费者必须解析后按编号数值比较，不能按字符串字典序比较
 * @param userId     用户服务内部用户编号，不提供公开用户编号接口
 * @param targetType PRODUCT 菜品或 WINDOW 店铺
 * @param targetId   菜品或店铺编号
 * @param active     当前变更后的收藏状态
 * @param changedAt  用户库记录的变更时间；并发和乱序时以 eventId 数值而不是时间判断新旧
 */
public record FavoriteChangedEvent(String eventId, Long userId, String targetType, Long targetId,
                                   Boolean active, LocalDateTime changedAt) {
    /**
     * 从用户库已提交发件箱行构建事件；同一行重发时保持完全相同的事件 ID。
     */
    public static FavoriteChangedEvent from(FavoriteEventOutboxRow row) {
        if (row == null || row.getId() == null || row.getId() <= 0 || row.getUserId() == null
                || row.getUserId() <= 0 || row.getTargetId() == null || row.getTargetId() <= 0
                || !("PRODUCT".equals(row.getTargetType()) || "WINDOW".equals(row.getTargetType()))
                || row.getActive() == null || row.getChangedAt() == null) {
            throw new IllegalArgumentException("收藏发件箱数据不完整");
        }
        return new FavoriteChangedEvent("favorite:" + row.getId(), row.getUserId(), row.getTargetType(),
                row.getTargetId(), row.getActive(), row.getChangedAt());
    }
}
