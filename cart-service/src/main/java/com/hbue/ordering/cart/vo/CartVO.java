package com.hbue.ordering.cart.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 当前登录顾客的购物车展示结果，不返回其它用户的数据。
 *
 * @param windows 按窗口 ID 排序的分组
 * @param totalQuantity 全部条目的购买数量合计，含失效和未勾选项
 * @param checkedQuantity 已勾选且可售的菜品数量合计
 * @param checkedAmount 已勾选且可售的预览金额合计，实际下单仍逐窗口创建
 * @author order-system
 * @date 2026-10-17
 */
public record CartVO(List<CartWindowVO> windows, long totalQuantity,
                     long checkedQuantity, BigDecimal checkedAmount) {
}
