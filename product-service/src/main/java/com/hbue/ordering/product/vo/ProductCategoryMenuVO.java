package com.hbue.ordering.product.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 顾客菜单中的一个启用分类及其在售菜品列表。
 *
 * <p>由商品服务菜单查询组装；即使分类下暂时没有在售菜品，也会保留该分类，
 * 以维持目录结构的展示语义。</p>
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
