package com.hbue.ordering.product.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 窗口菜品持久化对象。
 *
 * <p>映射 product_db 中的 products 表；每条菜品记录只属于一个窗口。</p>
 *
 * @author order-system
 * @date 2026-09-28
 */
@Data
@TableName("products")
public class ProductDO {

    /**
     * 菜品 ID。
     */
    @TableId(value = "product_id", type = IdType.AUTO)
    private Long productId;

    /**
     * 所属窗口 ID。
     */
    @TableField("window_id")
    private Long windowId;

    /**
     * 所属分类 ID。
     */
    @TableField("category_id")
    private Long categoryId;

    /**
     * 窗口内的菜品编码。
     */
    @TableField("product_code")
    private String productCode;

    /**
     * 菜品名称。
     */
    @TableField("product_name")
    private String productName;

    /**
     * 菜品描述。
     */
    @TableField("description")
    private String description;

    /**
     * 菜品图片地址。
     */
    @TableField("image_url")
    private String imageUrl;

    /**
     * 当前窗口售价。
     */
    @TableField("price")
    private BigDecimal price;

    /**
     * 售卖状态：1 在售，0 下架。
     */
    @TableField("sale_status")
    private Integer saleStatus;

    /**
     * 菜品排序值。
     */
    @TableField("sort_order")
    private Integer sortOrder;

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
