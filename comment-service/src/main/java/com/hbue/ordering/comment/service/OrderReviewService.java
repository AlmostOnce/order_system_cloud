package com.hbue.ordering.comment.service;

import com.hbue.ordering.comment.vo.OrderReviewsVO;

/**
 * 订单维度的评价查询。
 *
 * <p>一笔订单可能包含多个店铺菜品，各自可独立评价，因此返回的是订单下全部评价的集合视图。
 * 查询前需确认订单归属：调用方传入原始 Bearer 凭据，由订单服务按其自身鉴权规则确认
 * "该订单属于该用户"，评论服务不自行缓存或信任 userId 与 orderId 的关联。</p>
 *
 * @author order-system
 * @date 2026-10-28
 */
public interface OrderReviewService {

    /**
     * 查询指定订单下当前用户可见的评价。
     *
     * @param userId  当前登录用户编号
     * @param orderId 订单主键
     * @param bearer  原始 Authorization 头，用于向订单服务证明调用者身份
     * @return 订单下全部评价视图
     */
    OrderReviewsVO get(Long userId, Long orderId, String bearer);
}
