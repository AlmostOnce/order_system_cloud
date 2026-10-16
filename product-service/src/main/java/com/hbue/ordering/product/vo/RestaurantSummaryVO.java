package com.hbue.ordering.product.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 顾客浏览启用餐厅列表时使用的轻量摘要视图。
 *
 * <p>只包含选择餐厅所需的展示字段，不包含状态、展示排序等管理端字段；
 * 排序由查询语句保证，调用方按返回顺序直接渲染即可。</p>
 *
 * @author order-system
 * @date 2026-10-16
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantSummaryVO {

    /** 餐厅 ID。 */
    private Long restaurantId;

    /** 餐厅编码。 */
    private String restaurantCode;

    /** 餐厅名称。 */
    private String restaurantName;

    /** 餐厅位置。 */
    private String location;
}
