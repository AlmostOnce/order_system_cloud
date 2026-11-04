package com.hbue.ordering.order.model;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
@Data
@TableName("order_timeout_outbox")
public class OrderTimeoutOutboxDO {
    @TableId(type=IdType.INPUT) private String eventId;
    private Long orderId;
    private Long expiresAt;
}
