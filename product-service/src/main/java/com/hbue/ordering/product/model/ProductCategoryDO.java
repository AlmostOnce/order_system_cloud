package com.hbue.ordering.product.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 窗口菜品分类持久化对象。
 *
 * <p>映射 product_db 中的 product_categories 表。</p>
 *
 * @author order-system
 * @date 2026-09-28
 */
@Data
@TableName("product_categories")
public class ProductCategoryDO {

    /**
     * 分类 ID。
     */
    @TableId(value = "category_id", type = IdType.AUTO)
    private Long categoryId;

    /**
     * 所属窗口 ID。
     */
    @TableField("window_id")
    private Long windowId;

    /**
     * 分类名称。
     */
    @TableField("category_name")
    private String categoryName;

    /**
     * 分类排序值。
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * 分类状态：1 启用，0 停用。
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
