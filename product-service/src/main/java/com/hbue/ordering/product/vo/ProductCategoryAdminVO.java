package com.hbue.ordering.product.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 商品分类管理接口返回对象。
 *
 * @author order-system
 * @date 2026-09-28
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductCategoryAdminVO {

    /** 分类 ID。 */
    private Long categoryId;

    /** 所属窗口 ID。 */
    private Long windowId;

    /** 分类名称。 */
    private String categoryName;

    /** 分类排序值。 */
    private Integer sortOrder;

    /** 分类状态：1 启用，0 停用。 */
    private Integer status;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 修改时间。 */
    private LocalDateTime updatedAt;
}
