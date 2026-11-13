package com.hbue.ordering.statistics.vo;

import java.util.List;

/** 单个窗口当前月的窗口总份数及菜品份数。 */
public record WindowProductMonthlySalesVO(Long windowId, long sales, List<ProductItem> products) {
    /** 单道菜当前月的已支付份数。 */
    public record ProductItem(Long productId, long sales) {
    }
}
