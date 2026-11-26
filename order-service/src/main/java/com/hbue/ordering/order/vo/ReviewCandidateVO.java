package com.hbue.ordering.order.vo;

/**
 * 待评价统计用的订单明细候选。
 *
 * <p>一行对应一条订单明细，同一个订单会因菜品数量返回多行；调用方按 {@code orderId}
 * 聚合后再判断店铺评价与各菜品评价是否齐全。这里只暴露编号，不返回金额、收货信息
 * 或用户身份，避免内部接口携带与评价判定无关的数据。</p>
 *
 * @param orderId   订单编号
 * @param windowId  档口编号
 * @param productId 菜品编号
 * @author order-system
 * @date 2026-11-26
 */
public record ReviewCandidateVO(Long orderId, Long windowId, Long productId) {
}
