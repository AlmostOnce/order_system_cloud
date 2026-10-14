package com.hbue.ordering.order.vo;

import com.hbue.ordering.order.model.OrderDO;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单服务提供给受信任内部调用方的最小订单视图。
 *
 * <p>与顾客侧 OrderDetailVO 不同，内部视图不含菜品快照等详情，
 * 且仅能通过共享密钥内部接口访问。该类型只暴露支付侧校验订单归属、金额和状态所需字段，
 * 不作为顾客接口响应。</p>
 *
 * @author order-system
 * @date 2026-10-12
 */
@Data
@Builder
public class OrderInternalVO {

    /** 订单 ID。 */
    private Long orderId;

    /** 订单号。 */
    private String orderNo;

    /** 下单用户 ID。 */
    private Long userId;

    /** 订单总金额。 */
    private BigDecimal totalAmount;

    /** 订单当前状态（数据库中文值）。 */
    private String status;

    /**
     * 从订单持久化对象构建内部视图。
     *
     * @param orderDO 订单持久化对象
     * @return 内部订单视图
     */
    public static OrderInternalVO from(OrderDO orderDO) {
        return OrderInternalVO.builder()
                .orderId(orderDO.getOrderId())
                .orderNo(orderDO.getOrderNo())
                .userId(orderDO.getUserId())
                .totalAmount(orderDO.getTotalAmount())
                .status(orderDO.getStatus())
                .build();
    }
}
