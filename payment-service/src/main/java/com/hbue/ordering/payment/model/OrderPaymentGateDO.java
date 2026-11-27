package com.hbue.ordering.payment.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("order_payment_gate")
public class OrderPaymentGateDO {
    @TableId(type = IdType.INPUT)
    private Long orderId;
    private Integer closing;
}
