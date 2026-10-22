package com.hbue.ordering.product.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 顾客可见的窗口菜单响应视图。
 *
 * <p>包含窗口展示信息、启用分类及无分类的在售菜品；只表示查询时刻的菜单快照，
 * 不承担下单时的价格或可售状态校验。</p>
 *
 * @author order-system
 * @date 2026-09-28
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WindowMenuVO {

    /** 窗口 ID。 */
    private Long windowId;

    /** 窗口编码。 */
    private String windowCode;

    /** 窗口名称。 */
    private String windowName;

    /** 窗口简介。 */
    private String description;

    /** 窗口位置。 */
    private String location;

    /** 窗口下启用的分类，保留没有在售菜品的空分类。 */
    private List<ProductCategoryMenuVO> categories;

    /** 未关联分类但当前在售的菜品。 */
    private List<MenuProductVO> uncategorizedProducts;

    /** 窗口封面地址。 */
    private String imageUrl;

    public WindowMenuVO(Long windowId, String windowCode, String windowName, String description,
            String location, List<ProductCategoryMenuVO> categories, List<MenuProductVO> uncategorizedProducts) {
        this(windowId, windowCode, windowName, description, location, categories, uncategorizedProducts, null);
    }
}
