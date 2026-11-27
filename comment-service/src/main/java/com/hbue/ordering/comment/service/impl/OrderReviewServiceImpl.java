package com.hbue.ordering.comment.service.impl;

import com.hbue.ordering.comment.client.CommentOrderClient;
import com.hbue.ordering.comment.service.CommentService;
import com.hbue.ordering.comment.service.OrderReviewService;
import com.hbue.ordering.comment.service.WindowReviewService;
import com.hbue.ordering.comment.vo.OrderReviewsVO;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import feign.FeignException;
import org.springframework.stereotype.Service;

/**
 * 订单维度评价查询的编排实现。
 *
 * <p>先用调用方原始 Bearer 凭据向订单服务确认订单归属与完成状态，再分别读取店铺评价与菜品评价，
 * 合并成订单视图。归属校验必须在读取评价之前完成，否则只凭订单编号就能读到他人的评价内容。</p>
 *
 * <p>远端失败映射：401 视为未登录，403/404 统一视为不存在（不暴露订单归属），其余返回 503。</p>
 *
 * @author order-system
 * @date 2026-10-28
 */
@Service
public class OrderReviewServiceImpl implements OrderReviewService {

    /**
     * 订单服务内部客户端。
     */
    private final CommentOrderClient orders;

    /**
     * 菜品评价查询。
     */
    private final CommentService comments;

    /**
     * 店铺评价查询。
     */
    private final WindowReviewService windows;

    /**
     * 创建订单评价查询服务。
     *
     * @param orders   订单服务内部客户端
     * @param comments 菜品评价查询
     * @param windows  店铺评价查询
     */
    public OrderReviewServiceImpl(CommentOrderClient orders, CommentService comments, WindowReviewService windows) {
        this.orders = orders;
        this.comments = comments;
        this.windows = windows;
    }

    public OrderReviewsVO get(Long userId, Long orderId, String bearer) {
        // 先经订单服务校验本人归属；不能只凭订单编号读取其他用户的评价。
        try {
            var response = orders.getOrder(orderId, bearer);
            if (response == null || !CommonErrorCode.SUCCESS.code().equals(response.getCode()) || response.getData() == null
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
