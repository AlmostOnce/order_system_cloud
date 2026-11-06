package com.hbue.ordering.comment.service;

import com.hbue.ordering.comment.dto.CreateWindowReviewRequest;
import com.hbue.ordering.comment.vo.WindowReviewPageVO;
import com.hbue.ordering.comment.vo.WindowReviewVO;

public interface WindowReviewService {
    WindowReviewVO create(Long userId, String author, String bearer, String key, CreateWindowReviewRequest request);
    WindowReviewVO forOrder(Long userId, Long orderId);
    /** score为空查询全部，只允许1或5；hasImages=true仅查有图，采用有界游标分页，用户身份由认证主体提供。 */
    WindowReviewPageVO page(Long userId, Long windowId, String cursor, int limit, Integer score, Boolean hasImages);
    com.hbue.ordering.comment.vo.WindowRatingVO rating(Long windowId);
    /** 返回目标全部可见评价统计，不受当前页或当前筛选影响。 */
    com.hbue.ordering.comment.vo.ReviewSummaryVO summary(Long windowId);
}
