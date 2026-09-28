package com.hbue.ordering.order.vo;

import com.hbue.ordering.order.model.OrderItemDO;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 对外返回的订单菜品快照。
 *
 * @author order-system
 * @date 2026-09-28
 */
@Getter
@Builder
public class OrderItemVO {

    /** 商品服务菜品 ID。 */
    private Long productId;

    /** 下单时的商品编码。 */
    private String productCode;

    /** 下单时的菜品名称。 */
    private String productName;

    /** 下单时的商品单价。 */
    private BigDecimal unitPrice;

    /** 购买数量。 */
    private Integer quantity;

    /** 下单时的行金额。 */
    private BigDecimal lineAmount;

    /**
     * 从订单明细持久化对象构造响应视图。
     *
     * @param itemDO 订单明细持久化对象
     * @return 订单明细视图对象
     */
    public static OrderItemVO from(OrderItemDO itemDO) {
        OrderItemDO item = Objects.requireNonNull(itemDO, "itemDO must not be null");
        return OrderItemVO.builder()
                .productId(item.getProductId())
                .productCode(item.getProductCodeSnapshot())
                .productName(item.getProductNameSnapshot())
                .unitPrice(item.getUnitPriceSnapshot())
                .quantity(item.getQuantity())
                .lineAmount(item.getLineAmount())
                .build();
    }
}
