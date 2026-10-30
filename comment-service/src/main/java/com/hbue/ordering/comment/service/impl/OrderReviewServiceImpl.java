package com.hbue.ordering.comment.service.impl;

import com.hbue.ordering.comment.client.CommentOrderClient;
import com.hbue.ordering.comment.service.*;
import com.hbue.ordering.comment.vo.OrderReviewsVO;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import feign.FeignException;
import org.springframework.stereotype.Service;

@Service
public class OrderReviewServiceImpl implements OrderReviewService {
    private final CommentOrderClient orders;
    private final CommentService comments;
    private final WindowReviewService windows;
    public OrderReviewServiceImpl(CommentOrderClient orders, CommentService comments, WindowReviewService windows) {
        this.orders = orders; this.comments = comments; this.windows = windows;
    }
    public OrderReviewsVO get(Long userId, Long orderId, String bearer) {
        // 先经订单服务校验本人归属；不能只凭订单编号读取其他用户的评价。
        try {
            var response = orders.getOrder(orderId, bearer);
            if (response == null || !"COMMON_000".equals(response.getCode()) || response.getData() == null
                    || !orderId.equals(response.getData().orderId()))
                throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "无法确认本人订单");
            if (!"已完成".equals(response.getData().status()))
                throw new BusinessException(CommonErrorCode.CONFLICT, "订单完成后才能评价");
        } catch (FeignException e) {
            throw new BusinessException(e.status() == 401 ? CommonErrorCode.UNAUTHORIZED
                    : e.status() == 403 || e.status() == 404 ? CommonErrorCode.NOT_FOUND
                    : CommonErrorCode.SERVICE_UNAVAILABLE, "无法确认本人订单");
        }
        return new OrderReviewsVO(orderId, windows.forOrder(userId, orderId), comments.forOrder(userId, orderId));
    }
}
