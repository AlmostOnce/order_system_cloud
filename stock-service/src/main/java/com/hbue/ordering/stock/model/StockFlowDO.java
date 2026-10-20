package com.hbue.ordering.stock.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存流水持久化对象。
 *
 * <p>映射 {@code stock_db.stock_flow} 表，记录每一次库存变动的前后数量与原因。
 * 唯一键 {@code (biz_type, biz_ref, stock_id)} 是幂等核心：
 * 同一订单同一菜品的同类变动只允许记账一次，重复请求会被数据库直接拒绝。</p>
 *
 * <p>本表同时是对账依据：单条库存记录的全部流水累计值必须与主表
 * {@code available_qty} 的初始值 + 累计变动一致。</p>
 *
 * @author order-system
 * @date 2026-10-19
 */
@Data
@TableName("stock_flow")
public class StockFlowDO {

    /**
     * 流水主键。
     */
    @TableId(value = "flow_id", type = IdType.AUTO)
    private Long flowId;

    /**
     * 业务类型：ORDER_DEDUCT / ORDER_RESTORE / ADMIN_ADJUST。
     */
    @TableField("biz_type")
    private String bizType;

    /**
     * 业务单据号（订单号），与业务类型一起用于幂等。
     */
    @TableField("biz_ref")
    private String bizRef;

    /**
     * 库存记录 ID。
     */
    @TableField("stock_id")
    private Long stockId;

    /**
     * 档口 ID（冗余，便于按档口对账）。
     */
    @TableField("window_id")
    private Long windowId;

    /**
     * 菜品 ID（冗余）。
     */
    @TableField("product_id")
    private Long productId;

    /**
     * 变动数量：扣减为负、回补为正。
     */
    @TableField("change_qty")
    private Integer changeQty;

    /**
     * 变动前可售数量。
     */
    @TableField("before_qty")
    private Integer beforeQty;

    /**
     * 变动后可售数量。
     */
    @TableField("after_qty")
    private Integer afterQty;

    /**
     * 操作者 user_id，系统触发可为空。
     */
    @TableField("operator_id")
    private Long operatorId;

    /**
     * 备注。
     */
    @TableField("remark")
    private String remark;

    /**
     * 创建时间。
     */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
