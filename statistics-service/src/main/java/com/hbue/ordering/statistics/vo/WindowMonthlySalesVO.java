package com.hbue.ordering.statistics.vo;

import java.util.List;

/** 首页窗口月售批量响应；不存在销量的窗口由学生端显示为0份。 */
public record WindowMonthlySalesVO(List<Item> items) {
    /** 单个窗口当月的已支付份数。 */
    public record Item(Long windowId, long sales) {
    }
}
