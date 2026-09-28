package com.hbue.ordering.product.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 菜单中当前在售的菜品。
 *
 * @author order-system
 * @date 2026-09-28
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MenuProductVO {

    /** 菜品 ID。 */
    private Long productId;

    /** 窗口内菜品编码。 */
    private String productCode;

    /** 菜品名称。 */
    private String productName;

    /** 菜品描述。 */
    private String description;

    /** 菜品图片地址。 */
    private String imageUrl;

    /** 当前售价。 */
    private BigDecimal price;

    /** 窗口内排序值。 */
    private Integer sortOrder;
}
