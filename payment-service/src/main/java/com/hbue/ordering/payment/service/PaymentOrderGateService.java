package com.hbue.ordering.payment.service;

import java.util.function.Supplier;

/**
 * 订单支付入口的开关闸门。
 *
 * <p>支付回调与订单关闭可能并发到达：回调必须先在事务内确认"入口仍开放"再执行记账动作，
 * 关单则先把入口置为关闭并加锁，二者通过同一行记录的行锁串行化，从而保证不会出现
 * "订单已关闭却又被支付成功"的双写状态。</p>
 *
 * <p>闸门状态按订单各占一行，首次使用时自动补行，因此不依赖订单服务先建记录。</p>
 *
 * @author order-system
 * @date 2026-10-30
 */
public interface PaymentOrderGateService {

    /**
     * 在确认支付入口开放的前提下执行动作。
     *
     * <p>闸门判断与业务动作处于同一事务：动作抛异常会同时回滚，闸门不会被误标为已处理。</p>
     *
     * @param orderId 订单主键
     * @param action  需要与闸门同事务执行的业务动作
     * @param <T>     返回值类型
     * @return 业务动作的返回值
     */
    <T> T whileOpen(Long orderId, Supplier<T> action);

    /**
     * 关闭支付入口，阻止后续支付回调继续记账。
     *
     * <p>本方法幂等：重复关单只会重复置为关闭，不会报错。</p>
     *
     * @param orderId 订单主键
     */
    void beginClose(Long orderId);

    /**
     * 撤销关闭意图，恢复支付入口。
     *
     * <p>订单取消在关单之后还可能因回补库存、释放优惠券等步骤失败而整体回滚；此时订单仍是
     * "待支付"，支付入口不能保持在关闭状态，否则用户既不能取消也不能支付。撤销同样在行锁内执行，
     * 与并发的关单、支付回调串行；本方法幂等。</p>
     *
     * @param orderId 订单主键
     */
    void reopen(Long orderId);
}
