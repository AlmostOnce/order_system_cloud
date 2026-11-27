package com.hbue.ordering.comment.service;

/**
 * 待评价订单统计。
 *
 * <p>“待评价”指已完成订单中，店铺评价缺失，或该订单任一菜品评价缺失。
 * 评价服务只保存评价，不保存订单，因此需要向订单服务读取最近已完成订单的明细，
 * 再与自己的评价表比对。该统计只用于个人中心入口角标，不参与评分、榜单或权限判断。</p>
 *
 * @author order-system
 * @date 2026-11-26
 */
public interface PendingReviewService {

    /**
     * 统计用户的待评价订单数。
     *
     * @param userId 当前登录用户内部编号
     * @return 待评价订单数；没有已完成订单或全部已评价时返回 0
     */
    int count(Long userId);
}
