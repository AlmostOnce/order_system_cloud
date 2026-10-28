package com.hbue.ordering.comment.service;

import com.hbue.ordering.comment.api.ProductRatingVO;

/** RatingSummaryService 业务能力边界。 */
public interface RatingSummaryService {
    ProductRatingVO get(Long productId);
}
