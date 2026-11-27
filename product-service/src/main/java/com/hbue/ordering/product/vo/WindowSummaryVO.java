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

    /**
     * 窗口 ID。
     */
    private Long windowId;

    /**
     * 所属餐厅 ID，供顾客端按餐厅筛选档口。
     */
    private Long restaurantId;

    /**
     * 窗口编码。
     */
    private String windowCode;

    /**
     * 窗口名称。
     */
    private String windowName;

    /**
     * 窗口简介。
     */
    private String description;

    /**
     * 窗口展示排序，越小越靠前。
     */
    private Integer sortOrder;

    /**
     * 窗口位置。
     */
    private String location;

    /**
     * 文件服务图片地址。
     */
    private String imageUrl;

    /**
     * 是否营业：1 营业，0 休息；与启用状态相互独立。
     *
     * <p>顾客端需要据此展示“休息中”并停止加购，因此必须进入面向顾客的摘要视图，
     * 不能只在管理端返回。旧缓存缺少该字段时按 null 处理，由客户端兼容为营业中。</p>
     */
    private Integer isOpen;

    public WindowSummaryVO(Long windowId, Long restaurantId, String windowCode, String windowName,
                           String description, Integer sortOrder, String location) {
        this(windowId, restaurantId, windowCode, windowName, description, sortOrder, location, null, null);
    }
}
