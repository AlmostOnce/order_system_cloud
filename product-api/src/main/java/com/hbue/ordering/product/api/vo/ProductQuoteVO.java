package com.hbue.ordering.product.api.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 商品服务返回的订单价格快照来源。
 *
 * @author order-system
 * @date 2026-09-28
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductQuoteVO {

    /** 菜品 ID。 */
    private Long productId;

    /** 窗口内菜品编码。 */
    private String productCode;

    /** 菜品名称。 */
    private String productName;

    /** 商品服务查询到的当前单价。 */
    private BigDecimal price;
}
