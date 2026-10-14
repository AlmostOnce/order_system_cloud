package com.hbue.ordering.product.api.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 商品服务核价结果中的单个可售商品视图。
 *
 * <p>此类型用于服务间传递商品服务读取到的当前商品标识、名称和单价，
 * 供订单服务在创建订单时形成价格快照；它不是商品表实体，也不代表后续价格。</p>
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
