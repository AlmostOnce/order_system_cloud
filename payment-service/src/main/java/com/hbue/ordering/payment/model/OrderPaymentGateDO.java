package com.hbue.ordering.payment.model;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
@Data
@TableName("order_payment_gate")
public class OrderPaymentGateDO { @TableId(type=IdType.INPUT) private Long orderId; private Integer closing; }
