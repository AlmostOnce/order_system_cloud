package com.hbue.ordering.comment.service;

import com.hbue.ordering.comment.dto.CreateWindowReviewRequest;
import com.hbue.ordering.comment.vo.WindowReviewPageVO;
import com.hbue.ordering.comment.vo.WindowReviewVO;

public interface WindowReviewService {
    WindowReviewVO create(Long userId, String author, String bearer, String key, CreateWindowReviewRequest request);
    WindowReviewPageVO page(Long userId, Long windowId, String cursor, int limit);
    com.hbue.ordering.comment.vo.WindowRatingVO rating(Long windowId);
}
