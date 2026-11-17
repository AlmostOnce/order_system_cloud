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

    /** 下单时规格选项 ID 的规范化组合键；base 表示没有规格选择。 */
    @TableField("selection_key_snapshot")
    private String selectionKeySnapshot;

    /** 下单时的规格组与选项文字快照，避免规格改名后历史订单展示变化。 */
    @TableField("specification_text_snapshot")
    private String specificationTextSnapshot;

    /**
     * 商品核价返回的结构化规格选择 JSON，保存当时的组/选项 ID 与名称；不随商品规格改名变动。
     * 旧订单迁移为 []，表示没有可恢复的结构化事实，不能用当前目录伪造历史选择。
     */
    @TableField("specification_selection_snapshot")
    private String specificationSelectionSnapshot;

    /** 购买数量。 */
    private Integer quantity;

    /** 有效申请占用数量（含已退款），关闭申请才释放，未批准申请不暂停制作。 */
    private Integer refundReservedQuantity;
    /** 已批准数量（含执行中/成功），不再属于可履约数量。 */
    private Integer refundApprovedQuantity;
    /** 完成资金及所需库存步骤的退款数量。 */
    private Integer refundedQuantity;

    /** 当前明细行金额快照。 */
    @TableField("line_amount")
    private BigDecimal lineAmount;

    /** 订单级优惠分摊到本明细的金额；退款时不得按原价返还这部分优惠。 */
    @TableField("line_discount_amount")
    private BigDecimal lineDiscountAmount;

    /** 本明细优惠后的实付金额，按份退款以此金额快照为基数。 */
    @TableField("line_payable_amount")
    private BigDecimal linePayableAmount;

    @TableField("created_at")
    /** 明细创建时间。 */
    private LocalDateTime createdAt;
}
