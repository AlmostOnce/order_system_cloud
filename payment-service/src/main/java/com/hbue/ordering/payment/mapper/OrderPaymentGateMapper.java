package com.hbue.ordering.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.payment.model.OrderPaymentGateDO;
import org.apache.ibatis.annotations.*;

@Mapper
public interface OrderPaymentGateMapper extends BaseMapper<OrderPaymentGateDO> {
    @Insert("INSERT INTO order_payment_gate(order_id) VALUES(#{id}) ON DUPLICATE KEY UPDATE order_id=order_id")
    int ensure(@Param("id") Long id);

    @Select("SELECT closing FROM order_payment_gate WHERE order_id=#{id} FOR UPDATE")
    Integer lock(@Param("id") Long id);

    @Update("UPDATE order_payment_gate SET closing=1 WHERE order_id=#{id}")
    int beginClose(@Param("id") Long id);

    /**
     * 撤销"已开始关单"的关闭意图，恢复支付入口。
     *
     * <p>closing 写在支付库，不随订单服务的 Seata 全局事务回滚。订单取消失败后如果只关闭不撤销，
     * 订单仍处于"待支付"却会被永久拒绝发起支付，直到超时自动取消。调用方必须已持有同一行的行锁，
     * 保证撤销与并发的关单/支付回调串行执行。</p>
     *
     * @param id 订单主键
     * @return 受影响行数；already 0 表示原本就是开放状态
     */
    @Update("UPDATE order_payment_gate SET closing=0 WHERE order_id=#{id} AND closing<>0")
    int reopen(@Param("id") Long id);
}
