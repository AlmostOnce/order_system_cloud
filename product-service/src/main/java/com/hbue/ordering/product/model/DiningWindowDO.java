package com.hbue.ordering.product.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 窗口持久化对象。
 *
 * <p>映射 product_db 中的 dining_windows 表。</p>
 *
 * @author order-system
 * @date 2026-09-28
 */
@Data
@TableName("dining_windows")
public class DiningWindowDO {

    /**
     * 窗口 ID。
     */
    @TableId(value = "window_id", type = IdType.AUTO)
    private Long windowId;

    /**
     * 窗口编码。
     */
    @TableField("window_code")
    private String windowCode;

    /**
     * 窗口名称。
     */
    @TableField("window_name")
    private String windowName;

    /**
     * 窗口位置。
     */
    @TableField("location")
    private String location;

    /**
     * 窗口状态：1 启用，0 停用。
     */
    @TableField("status")
    private Integer status;

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
