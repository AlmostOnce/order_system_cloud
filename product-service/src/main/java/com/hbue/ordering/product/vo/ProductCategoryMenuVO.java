package com.hbue.ordering.product.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 菜单分类及其在售菜品。
 *
 * @author order-system
 * @date 2026-09-28
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductCategoryMenuVO {

    /** 分类 ID。 */
    private Long categoryId;

    /** 分类名称。 */
    private String categoryName;

    /** 分类排序值。 */
    private Integer sortOrder;

    /** 当前分类下的在售菜品。 */
    private List<MenuProductVO> products;
}
