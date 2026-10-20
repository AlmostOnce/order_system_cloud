package com.hbue.ordering.cart.vo;

import java.math.BigDecimal;

/**
 * 一项菜品的购物车展示结果；价格只用于预览，创建订单时重新核价。
 *
 * @param productId 菜品 ID
 * @param productName 当前名称；不可售时使用保存的名称快照
 * @param productCode 当前编码；不可售时使用编码快照
 * @param quantity 购买数量
 * @param checked 用户勾选状态
 * @param revision 本项修改版本
 * @param available 当前是否可以结算
 * @param unavailableReason 不可结算原因，可售时为 null
 * @param unitPrice 当前单价；不可售时为 null，不能伪造为零元菜品
 * @param lineAmount 当前行金额；不可售时为 null
 * @author order-system
 * @date 2026-10-17
 */
public record CartItemVO(Long productId, String productName, String productCode,
                         int quantity, boolean checked, String revision, boolean available,
                         String unavailableReason, BigDecimal unitPrice, BigDecimal lineAmount) {
}
