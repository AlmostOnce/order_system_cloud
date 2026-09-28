package com.hbue.ordering.order.dto.request;

import com.hbue.ordering.order.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理员推进订单履约状态的请求对象。
 *
 * @author order-system
 * @date 2026-09-28
 */
@Data
public class OrderStatusUpdateRequest {

    /** 希望推进到的目标状态，使用 OrderStatus 枚举名称。 */
    @NotNull(message = "订单目标状态不能为空")
    private OrderStatus status;
}
