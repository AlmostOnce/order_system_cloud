package com.hbue.ordering.statistics.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 已支付订单中一条菜品购买快照；原始购买数量不因退款事件而变更。 */
@Data
@TableName("statistics_sales_event_items")
public class SalesEventItemDO {
    /** 所属销量事件键。 */
    private String eventId;
    /** 订单明细主键，用于防止一笔订单内部重复写入同一行。 */
    private Long itemId;
    /** 下单时菜品主键。 */
    private Long productId;
    /** 下单时菜品名称快照。 */
    private String productName;
    /** 已支付订单中的原始购买份数。 */
    private Integer quantity;
}
