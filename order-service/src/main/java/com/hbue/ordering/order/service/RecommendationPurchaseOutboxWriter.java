package com.hbue.ordering.order.service;

/**
 * 在订单本地事务内登记支付成功或成功退款后的推荐全量快照。
 */
public interface RecommendationPurchaseOutboxWriter {
    /**
     * 读取当前订单版本与全部明细并持久化同版本事件；须在调用方订单事务中执行。
     *
     * @param orderId 已完成支付状态推进或资金确认成功退款的订单主键
     * @throws IllegalStateException 订单事实或Outbox写入失败时抛出，使调用方事务回滚
     */
    void recordCurrent(Long orderId);
}
