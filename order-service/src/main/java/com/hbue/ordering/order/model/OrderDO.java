package com.hbue.ordering.order.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("orders")
public class OrderDO {

  @TableId(value = "order_id", type = IdType.AUTO)
  private Long orderId;

  private String orderNo;

  private Long userId;

  private Long windowId;

  private BigDecimal totalAmount;

  private String status;

  private String remark;

  @TableField("idempotency_key")
  private String idempotencyKey;

  @TableField("request_hash")
  private String requestHash;

  private String pickupCode;

  @Version
  private Integer version;

  private LocalDateTime createdAt;

  private LocalDateTime updatedAt;
}
