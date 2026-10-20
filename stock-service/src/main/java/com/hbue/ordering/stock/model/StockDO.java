package com.hbue.ordering.stock.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存持久化对象。
 *
 * <p>映射 {@code stock_db.stock} 表，按 {@code (window_id, product_id)} 唯一标识一条
 * 档口菜品库存。数据库是库存的唯一事实来源，Redis 中的数量只是可再生的加速快照。</p>
 *
 * <p>{@code version} 作为乐观锁版本号，配合「可售数量不为负」的更新条件，
 * 保证并发扣减既不超卖也不互相覆盖。</p>
 *
 * @author order-system
 * @date 2026-10-19
 */
@Data
@TableName("stock")
public class StockDO {

    /**
     * 库存记录主键。
     */
    @TableId(value = "stock_id", type = IdType.AUTO)
    private Long stockId;

    /**
     * 所属档口 ID。
     */
    @TableField("window_id")
    private Long windowId;

    /**
     * 菜品 ID。
     */
    @TableField("product_id")
    private Long productId;

    /**
     * 总可售上限。
     */
    @TableField("total_qty")
    private Integer totalQty;

    /**
     * 当前可售数量，数据库值为准。
     */
    @TableField("available_qty")
    private Integer availableQty;

    /**
     * 乐观锁版本号，每次成功变更自增。
     */
    @TableField("version")
    private Integer version;

    /**
     * 逻辑删除：0 正常 1 已删。
     */
    @TableField("is_deleted")
    private Integer isDeleted;

    /**
     * 创建时间。
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 修改时间。
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
