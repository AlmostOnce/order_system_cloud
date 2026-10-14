package com.hbue.ordering.product.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 餐厅管理接口返回的视图对象。
 *
 * <p>包含餐厅管理端展示字段及持久化审计时间；不作为写请求或数据库映射对象，
 * 餐厅经理可见范围仍由 Service 层基于用户数据范围控制。</p>
 *
 * @author order-system
 * @date 2026-10-13
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantAdminVO {

    /** 餐厅 ID。 */
    private Long restaurantId;

    /** 餐厅编码。 */
    private String restaurantCode;

    /** 餐厅名称。 */
    private String restaurantName;

    /** 位置/楼层。 */
    private String location;

    /** 联系电话。 */
    private String phone;

    /** 门面图地址。 */
    private String imageUrl;

    /** 公告/简介。 */
    private String notice;

    /** 展示排序：值小的排前面。 */
    private Integer sortOrder;

    /** 餐厅状态：1 启用，0 停用。 */
    private Integer status;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 修改时间。 */
    private LocalDateTime updatedAt;
}
