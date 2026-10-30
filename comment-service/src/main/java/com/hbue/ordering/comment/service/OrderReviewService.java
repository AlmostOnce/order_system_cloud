package com.hbue.ordering.comment.service;

import com.hbue.ordering.comment.vo.OrderReviewsVO;

public interface OrderReviewService {
    OrderReviewsVO get(Long userId, Long orderId, String bearer);
}
