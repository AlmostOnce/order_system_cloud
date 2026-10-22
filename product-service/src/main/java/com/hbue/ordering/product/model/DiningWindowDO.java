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
 * <p>映射 product_db 中的 {@code dining_windows} 表，只承载表字段与 ORM 映射；
 * 窗口启停、营业状态和归属权限由 Service 层处理，DO 不作为接口响应类型。</p>
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
     * 所属餐厅 ID。
     */
    @TableField("restaurant_id")
    private Long restaurantId;

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

    /** 窗口简介。 */
    @TableField("description")
    private String description;

    /** 窗口展示排序：越小越靠前，相同值按窗口 ID 升序。 */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * 窗口位置。
     */
    @TableField("location")
    private String location;

    /**
     * 窗口图片地址。
     */
    @TableField("image_url")
    private String imageUrl;

    /**
     * 窗口状态：1 启用，0 停用。
     */
    @TableField("status")
    private Integer status;

    /**
     * 是否营业：1 营业，0 休息；与启用状态相互独立。
     */
    @TableField("is_open")
    private Integer isOpen;

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
