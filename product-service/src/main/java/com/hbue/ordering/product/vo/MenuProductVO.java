package com.hbue.ordering.product.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 顾客菜单中单个当前在售菜品的展示视图。
 *
 * <p>由商品服务根据数据库状态组装，仅包含菜单所需字段；不包含 DO 的持久化行为，
 * 也不承诺价格在后续下单时仍不变化，下单核价需调用内部商品契约。</p>
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
