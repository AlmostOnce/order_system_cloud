package com.hbue.ordering.comment.vo;

import java.time.LocalDateTime;
import java.util.List;

public record WindowReviewVO(String reviewId, Long windowId, String authorName, String authorAvatarUrl, int score,
        String content, List<String> images, List<String> tags, LocalDateTime createdAt) {}
