package com.hbue.ordering.statistics.model;

import lombok.Data;

/** 单个周期与窗口的已支付份数汇总。 */
@Data
public class SalesWindowTotalDO {
    /** 窗口主键。 */
    private Long windowId;
    /** 窗口当前周期内的已支付份数。 */
    private Long paidQuantity;
}
