package com.hbue.ordering.comment.model;
import java.time.LocalDateTime;
/** 内部元数据模型，不直接序列化到公开接口。 */
public record CommentRowDO(String commentId, Long userId, String authorName, Long orderId,
        Long productId, Long windowId, int score, String requestKey, String requestHash,
        LocalDateTime createdAt,
        // 元数据事务同步写入是否带图；null表示历史/旧代码写入记录尚未完成回填，不能当作无图。
        Boolean hasImages) {}
