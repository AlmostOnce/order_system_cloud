package com.hbue.ordering.order.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单主表持久化对象，对应 {@code orders} 表。
 *
 * <p>该对象只承载数据库字段，不作为 Controller 的请求或响应模型。
 * 订单金额和状态由订单业务服务校验后写入；{@code version} 用于状态更新时的乐观锁，
 * 幂等键及请求摘要用于识别同一用户的重复创建请求。</p>
 *
 * @author order-system
 * @date 2026-10-14
 */
@Data
@TableName("orders")
public class OrderDO {

  /** 订单表自增主键。 */
  @TableId(value = "order_id", type = IdType.AUTO)
  private Long orderId;

  /** 对外使用的订单编号，不等同于数据库主键。 */
  private String orderNo;

  /** 下单用户 ID；订单归属校验以此字段为准。 */
  private Long userId;

  /** 接单档口 ID；用于商品核价和商家数据范围校验。 */
  private Long windowId;

  /** 服务端根据商品服务价格和购买数量计算的订单总金额。 */
  private BigDecimal totalAmount;

  /** 数据库保存的当前订单状态中文值。 */
  private String status;

  /** 顾客提交的订单备注。 */
  private String remark;

  /** 顾客提交的幂等键；在用户维度唯一标识一次创建意图。 */
  @TableField("idempotency_key")
  private String idempotencyKey;

  /** 规范化订单请求的摘要，用于拒绝同一幂等键对应不同请求内容。 */
  @TableField("request_hash")
  private String requestHash;

  /** 订单支付成功后生成或保存的取餐码。 */
  private String pickupCode;

  /** 订单状态版本号；每次成功状态迁移递增，用于防止并发覆盖。 */
  @Version
  private Integer version;

  /** 订单创建时间。 */
  private LocalDateTime createdAt;

  /** 订单最近一次更新时间。 */
  private LocalDateTime updatedAt;
}
