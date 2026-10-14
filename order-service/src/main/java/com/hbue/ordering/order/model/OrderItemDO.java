package com.hbue.ordering.order.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单菜品明细持久化对象，对应 {@code order_items} 表。
 *
 * <p>商品编码、名称和单价均保存下单时的快照，避免商品后续改名或调价影响历史订单；
 * 该对象仅用于订单服务内部持久化，不直接作为对外接口响应。</p>
 *
 * @author order-system
 * @date 2026-09-28
 */
@Data
@TableName("order_items")
public class OrderItemDO {

    /** 明细主键。 */
    @TableId(value = "item_id", type = IdType.AUTO)
    private Long itemId;

    /** 所属订单主键。 */
    @TableField("order_id")
    private Long orderId;

    /** 商品服务菜品 ID。 */
    @TableField("product_id")
    private Long productId;

    /** 下单时保存的菜品编码。 */
    @TableField("product_code_snapshot")
    private String productCodeSnapshot;

    /** 下单时保存的菜品名称。 */
    @TableField("product_name_snapshot")
    private String productNameSnapshot;

    /** 下单时保存的商品单价。 */
    @TableField("unit_price_snapshot")
    private BigDecimal unitPriceSnapshot;

    /** 购买数量。 */
    private Integer quantity;

    /** 当前明细行金额快照。 */
    @TableField("line_amount")
    private BigDecimal lineAmount;

    @TableField("created_at")
    /** 明细创建时间。 */
    private LocalDateTime createdAt;
}
