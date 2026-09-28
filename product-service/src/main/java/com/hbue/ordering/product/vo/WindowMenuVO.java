package com.hbue.ordering.product.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 窗口菜单及窗口基本信息。
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

    /** 窗口位置。 */
    private String location;

    /** 窗口下启用的分类，保留没有在售菜品的空分类。 */
    private List<ProductCategoryMenuVO> categories;

    /** 未关联分类但当前在售的菜品。 */
    private List<MenuProductVO> uncategorizedProducts;
}
