package com.hbue.ordering.order.vo;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * 订单状态变更结果视图对象。
 *
 * @author order-system
 * @date 2026-09-28
 */
@Getter
@Builder
@ToString
public class OrderStatusVO {

    /** 已变更状态的订单 ID。 */
    private Long orderId;

    /** 变更后的订单状态中文名称。 */
    private String status;
}
