package com.hbue.ordering.order.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单菜品快照持久化对象。
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
