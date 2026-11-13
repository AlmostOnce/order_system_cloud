package com.hbue.ordering.statistics.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 统计服务本地持久化的已支付订单事件头。
 *
 * <p>eventId 与 orderId 双唯一约束承担消费幂等；paidAt 和 windowId 来自订单服务，不由统计服务推断。</p>
 */
@Data
@TableName("statistics_sales_events")
public class SalesEventDO {
    /** 稳定事件键，格式为 order-paid-sales:{orderId}。 */
    private String eventId;
    /** 来源订单主键，只在统计库内去重，不向学生端返回。 */
    private Long orderId;
    /** 下单时所属窗口主键快照。 */
    private Long windowId;
    /** 支付服务确认的首次支付时间。 */
    private LocalDateTime paidAt;
    /** 接收时的消息契约版本。 */
    private Integer eventVersion;
    /** 统计服务首次保存事件的时间。 */
    private LocalDateTime createdAt;
}
