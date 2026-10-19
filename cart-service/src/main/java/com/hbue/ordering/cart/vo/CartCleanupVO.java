package com.hbue.ordering.cart.vo;

/**
 * 清理操作结果，只描述购物车变化，不代表新建订单或再次扣款。
 *
 * @param orderId 已确认归属于当前用户的订单 ID
 * @param removedCount 实际删除的商品条目数，不是份数
 * @param skippedCount 已不存在或版本、数量、勾选状态发生变化的条目数
 */
public record CartCleanupVO(Long orderId, long removedCount, long skippedCount) {
}
