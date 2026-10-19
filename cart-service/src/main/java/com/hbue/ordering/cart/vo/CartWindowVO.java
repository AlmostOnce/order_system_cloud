package com.hbue.ordering.cart.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 按窗口分组的购物车，每次结算仅选择一个窗口。
 *
 * @param windowId 窗口 ID
 * @param windowActive 当前窗口是否启用
 * @param items 窗口内全部购物车项，包含不可售项
 * @param checkedAmount 本窗口已勾选且可售菜品的预览金额
 * @author order-system
 * @date 2026-10-17
 */
public record CartWindowVO(Long windowId, boolean windowActive, List<CartItemVO> items,
                           BigDecimal checkedAmount) {
}
