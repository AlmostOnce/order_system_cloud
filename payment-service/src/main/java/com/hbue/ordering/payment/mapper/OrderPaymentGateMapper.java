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
}
