package com.hbue.ordering.product.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 菜品管理接口返回对象。
 *
 * @author order-system
 * @date 2026-09-28
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductAdminVO {

    /** 菜品 ID。 */
    private Long productId;

    /** 所属窗口 ID。 */
    private Long windowId;

    /** 所属分类 ID；为空表示未分类。 */
    private Long categoryId;

    /** 窗口内菜品编码。 */
    private String productCode;

    /** 菜品名称。 */
    private String productName;

    /** 菜品描述。 */
    private String description;

    /** 菜品图片地址。 */
    private String imageUrl;

    /** 菜品售价。 */
    private BigDecimal price;

    /** 售卖状态：1 在售，0 下架。 */
    private Integer saleStatus;

    /** 菜品排序值。 */
    private Integer sortOrder;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 修改时间。 */
    private LocalDateTime updatedAt;
}
