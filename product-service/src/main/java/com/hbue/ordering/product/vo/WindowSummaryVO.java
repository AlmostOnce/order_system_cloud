package com.hbue.ordering.product.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 顾客浏览启用档口列表时使用的轻量摘要视图。
 *
 * <p>只包含列表展示所需字段，不包含管理状态、审计字段或持久化行为。</p>
 *
 * @author order-system
 * @date 2026-09-28
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WindowSummaryVO {

    /** 窗口 ID。 */
    private Long windowId;

    /** 所属餐厅 ID，供顾客端按餐厅筛选档口。 */
    private Long restaurantId;

    /** 窗口编码。 */
    private String windowCode;

    /** 窗口名称。 */
    private String windowName;

    /** 窗口简介。 */
    private String description;

    /** 窗口展示排序，越小越靠前。 */
    private Integer sortOrder;

    /** 窗口位置。 */
    private String location;
}
