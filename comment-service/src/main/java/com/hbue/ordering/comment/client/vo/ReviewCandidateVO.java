package com.hbue.ordering.comment.client.vo;

/**
 * 订单服务返回的待评价候选明细。
 *
 * <p>一行对应一条订单明细：同一订单会因菜品数量出现多行，评论服务必须按
 * {@code orderId} 聚合后再判断店铺评价与各菜品评价是否齐全。
 * 该对象只承载编号，不包含金额、收货地址或用户资料。</p>
 *
 * @param orderId   订单编号
 * @param windowId  档口编号
 * @param productId 菜品编号
 * @author order-system
 * @date 2026-11-26
 */
public record ReviewCandidateVO(Long orderId, Long windowId, Long productId) {
}
