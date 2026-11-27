package com.hbue.ordering.order.service;

/**
 * 订单支付超时的判定与关单入口。
 *
 * <p>由延迟消息触发 {@link #expire(Long)}：只有订单仍为"待支付"且已过支付截止时刻才真正关单，
 * 未到时限抛异常让 Broker 延迟重投，已支付或已取消则直接返回（幂等）。</p>
 *
 * <p>关单动作本身交由 {@code OrderService} 经 Spring 代理执行，以便参与既有的 Seata 全局事务、
 * 回补库存与释放优惠券，本服务不自行拼装补偿流程。</p>
 *
 * @author order-system
 * @date 2026-10-18
 */
public interface OrderTimeoutService {

    /**
     * 处理一条订单超时事件。
     *
     * @param orderId 订单主键
     */
    void expire(Long orderId);

    /**
     * 计算距离支付截止还剩多少毫秒，用于前端倒计时。
     *
     * <p>非待支付订单返回 0；订单尚不可见时抛异常，由消息重试等待主库可见。</p>
     *
     * @param orderId 订单主键
     * @return 剩余毫秒数，最小为 0
     */
    long remainingMillis(Long orderId);
}
