package com.hbue.ordering.order.service.impl;
import com.hbue.ordering.order.service.*;
import com.hbue.ordering.order.mapper.OrderMapper;
import org.springframework.stereotype.Service;
@Service
public class OrderTimeoutServiceImpl implements OrderTimeoutService {
    private final OrderMapper mapper;
    private final OrderService orders;
    public OrderTimeoutServiceImpl(OrderMapper mapper,OrderService orders) {this.mapper=mapper;this.orders=orders;}
    public void expire(Long id) {
        if(id==null || id<=0) throw new IllegalArgumentException("无效超时事件");
        var order=mapper.selectById(id);
        if(order==null) throw new IllegalStateException("超时事件对应订单尚不可见");
        if(!"待支付".equals(order.getStatus())) return;
        if(System.currentTimeMillis()<OrderPaymentDeadline.epochMillis(order))
            throw new IllegalStateException("订单尚未到支付截止时间，等待重试");
        // 经Spring代理进入现有Seata事务；不自行伪造登录态、不绕过库存回补。
        orders.cancelExpiredOrder(id);
    }
    public long remainingMillis(Long id) {
        if(id==null || id<=0)throw new IllegalArgumentException("无效订单编号");
        var order=mapper.selectById(id);
        if(order==null)throw new IllegalStateException("订单尚不可见");
        return "待支付".equals(order.getStatus())?Math.max(0,OrderPaymentDeadline.epochMillis(order)-System.currentTimeMillis()):0;
    }
}
