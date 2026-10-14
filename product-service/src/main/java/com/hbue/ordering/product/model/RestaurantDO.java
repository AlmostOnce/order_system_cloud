package com.hbue.ordering.product.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 餐厅持久化对象。
 *
 * <p>映射 product_db 中的 restaurants 表。餐厅是档口的上级层级：
 * 一个餐厅包含多个档口，餐厅经理按餐厅维度管辖。该类只映射数据库字段，
 * 不负责数据范围判断或餐厅管理业务，也不直接作为接口响应对象。</p>
 *
 * @author order-system
 * @date 2026-10-13
 */
@Data
@TableName("restaurants")
public class RestaurantDO {

    /**
     * 餐厅 ID。
     */
    @TableId(value = "restaurant_id", type = IdType.AUTO)
    private Long restaurantId;

    /**
     * 餐厅编码。
     */
    @TableField("restaurant_code")
    private String restaurantCode;

    /**
     * 餐厅名称。
     */
    @TableField("restaurant_name")
    private String restaurantName;

    /**
     * 位置/楼层，对应旧单体项目 canteen 表的 floor。
     */
    @TableField("location")
    private String location;

    /**
     * 联系电话。
     */
    @TableField("phone")
    private String phone;

    /**
     * 门面图地址。
     */
    @TableField("image_url")
    private String imageUrl;

    /**
     * 公告/简介。
     */
    @TableField("notice")
    private String notice;

    /**
     * 展示排序：值小的排前面，相同则按餐厅 ID 升序。
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * 餐厅状态：1 启用，0 停用。
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
