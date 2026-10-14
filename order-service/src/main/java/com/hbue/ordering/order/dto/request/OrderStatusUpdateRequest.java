package com.hbue.ordering.order.dto.request;

import com.hbue.ordering.order.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 商家推进订单履约状态的请求对象。
 *
 * <p>仅用于传递目标状态；调用权限、数据范围及从当前状态到目标状态的合法性，
 * 均由控制器安全配置和订单服务校验，不能通过请求对象绕过。</p>
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
