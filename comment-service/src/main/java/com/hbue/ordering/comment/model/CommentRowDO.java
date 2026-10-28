package com.hbue.ordering.comment.model;
import java.time.LocalDateTime;
/** 内部元数据模型，不直接序列化到公开接口。 */
public record CommentRowDO(String commentId, Long userId, String authorName, Long orderId,
        Long productId, Long windowId, int score, String requestKey, String requestHash,
        LocalDateTime createdAt) {}
