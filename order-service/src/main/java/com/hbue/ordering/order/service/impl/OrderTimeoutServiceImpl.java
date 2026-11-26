package com.hbue.ordering.order.service.impl;

import com.hbue.ordering.order.mapper.OrderMapper;
import com.hbue.ordering.order.service.OrderService;
import com.hbue.ordering.order.service.OrderTimeoutService;
import com.hbue.ordering.order.util.OrderPaymentDeadline;
import org.springframework.stereotype.Service;

/**
 * 订单支付超时判定与关单入口的实现。
 *
 * <p>判定与关单分离：先用只读查询确认状态与截止时间，再委派 {@link OrderService#cancelExpiredOrder}
 * 经 Spring 代理进入既有 Seata 全局事务，从而复用库存回补与优惠券释放逻辑，不在本类重复实现补偿。</p>
 *
 * <p>未到截止时间或订单尚不可见时抛异常而不是返回成功：异常让 Broker 延迟重投，
 * 返回成功会让消息被确认而订单永远不被关单。</p>
 *
 * @author order-system
 * @date 2026-10-18
 */
@Service
public class OrderTimeoutServiceImpl implements OrderTimeoutService {

    /**
     * 超时判定所需的只读订单查询。
     */
    private final OrderMapper mapper;

    /**
     * 关单业务入口，必须通过它进入全局事务。
     */
    private final OrderService orders;

    /**
     * 创建超时服务。
     *
     * @param mapper 订单查询
     * @param orders 关单业务入口
     */
    public OrderTimeoutServiceImpl(OrderMapper mapper, OrderService orders) {
        this.mapper = mapper;
        this.orders = orders;
    }

    /**
     * 处理一条超时事件：仅在订单仍待支付且已过截止时刻时关单。
     *
     * @param id 订单主键
     */
    @Override
    public void expire(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("无效超时事件");
        var order = mapper.selectById(id);
        if (order == null) throw new IllegalStateException("超时事件对应订单尚不可见");
        if (!"待支付".equals(order.getStatus())) return;
        if (System.currentTimeMillis() < OrderPaymentDeadline.epochMillis(order))
            throw new IllegalStateException("订单尚未到支付截止时间，等待重试");
        // 经Spring代理进入现有Seata事务；不自行伪造登录态、不绕过库存回补。
        orders.cancelExpiredOrder(id);
    }

    /**
     * 计算距离支付截止还剩多少毫秒。
     *
     * @param id 订单主键
     * @return 剩余毫秒数，非待支付或已超时返回 0
     */
    @Override
    public long remainingMillis(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("无效订单编号");
        var order = mapper.selectById(id);
        if (order == null) throw new IllegalStateException("订单尚不可见");
        return "待支付".equals(order.getStatus()) ? Math.max(0, OrderPaymentDeadline.epochMillis(order) - System.currentTimeMillis()) : 0;
    }
}
