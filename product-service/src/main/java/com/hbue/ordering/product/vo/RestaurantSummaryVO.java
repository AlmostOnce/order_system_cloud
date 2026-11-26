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
     * 餐厅位置。
     */
    private String location;

    /**
     * 餐厅公告/简介，由管理端维护；学生端在选择餐厅时展示，避免内容只在后台可见。
     */
    private String notice;

    /**
     * 餐厅联系电话，供学生找不到窗口或需要咨询时使用；未配置时为空。
     */
    private String phone;
}
