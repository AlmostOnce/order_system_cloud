package com.hbue.ordering.product.api.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 餐厅内部查询的最小视图对象。
 *
 * <p>供其他服务在数据范围校验时确认餐厅是否存在并读取展示所需的基本信息；
 * 只暴露服务间所需字段，不携带持久化对象和管理端敏感信息，属于内部契约。</p>
 *
 * @author order-system
 * @date 2026-10-13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantInfoVO {

    /**
     * 餐厅 ID。
     */
    private Long restaurantId;

    /**
     * 餐厅编码。
     */
    private String restaurantCode;

    /**
     * 餐厅名称。
     */
    private String restaurantName;

    /**
     * 餐厅状态：1 启用，0 停用。
     */
    private Integer status;
}
