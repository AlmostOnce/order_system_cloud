package com.hbue.ordering.statistics.model;

import lombok.Data;

/** 单个周期、窗口和菜品的 MySQL 汇总行，也是 Redis 排名丢失后的权威重建数据。 */
@Data
public class SalesProductTotalDO {
    /** 汇总周期代码：ALL 或 MONTH。 */
    private String periodType;
    /** ALL 周期固定为 ALLTIME，MONTH 周期为 YYYY-MM。 */
    private String periodKey;
    /** 下单时窗口主键。 */
    private Long windowId;
    /** 下单时菜品主键。 */
    private Long productId;
    /** 最近一次订单菜品名称快照。 */
    private String productName;
    /** 最近一次名称快照对应的订单明细主键。 */
    private Long latestItemId;
    /** 已支付的原始购买份数。 */
    private Long paidQuantity;
}
