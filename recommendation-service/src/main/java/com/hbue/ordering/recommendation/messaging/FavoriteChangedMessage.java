package com.hbue.ordering.recommendation.messaging;

import java.time.LocalDateTime;

/** 与 user-service Outbox JSON 对齐的收藏状态消息；版本取自事件编号中的数据库自增ID。 */
public record FavoriteChangedMessage(String eventId, Long userId, String targetType, Long targetId,
                                     Boolean active, LocalDateTime changedAt) { }
