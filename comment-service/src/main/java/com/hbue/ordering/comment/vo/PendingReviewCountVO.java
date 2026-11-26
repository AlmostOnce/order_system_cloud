package com.hbue.ordering.comment.vo;

/**
 * 待评价订单数量。
 *
 * <p>只返回数量，不返回订单号或菜品明细：个人中心入口只需要角标，
 * 具体订单仍从订单列表进入，避免把订单数据复制到评价服务前端。
 * 数量按最近有限批已完成订单统计，历史极多的账户可能略小于真实总数。</p>
 *
 * @param count 待评价订单数，非负
 * @author order-system
 * @date 2026-11-26
 */
public record PendingReviewCountVO(int count) {
}
