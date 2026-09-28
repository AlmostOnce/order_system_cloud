package com.hbue.ordering.order.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hbue.ordering.order.model.OrderDO;
import com.hbue.ordering.order.model.OrderItemDO;
import com.hbue.ordering.user.api.vo.UserBasicVO;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 订单详情视图对象。
 *
 * @author order-system
 * @date 2026-09-28
 */
@Getter
@Builder
@ToString
public class OrderDetailVO {

    /** 订单主键。 */
    private Long orderId;

    /** 订单编号。 */
    private String orderNo;

    /** 用户 ID。 */
    private Long userId;

    /** 用户名。 */
    private String username;

    /** 窗口 ID。 */
    private Long windowId;

    /** 服务端计算的订单总金额。 */
    private BigDecimal totalAmount;

    /** 订单状态。 */
    private String status;

    /** 订单备注。 */
    private String remark;

    /** 取餐码。 */
    private String pickupCode;

    /** 订单创建时间。 */
    private LocalDateTime createdAt;

    /** 订单更新时间。 */
    private LocalDateTime updatedAt;

    /** 下单时保存的菜品明细快照；列表摘要不查询此字段。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<OrderItemVO> items;

    /**
     * 从订单对象构造列表摘要。
     *
     * @param orderDO 订单持久化对象
     * @return 订单摘要视图
     */
    public static OrderDetailVO from(OrderDO orderDO) {
        return from(orderDO, null, null);
    }

    /**
     * 从订单对象和明细快照构造详情视图。
     *
     * @param orderDO 订单持久化对象
     * @param orderItems 订单明细快照
     * @return 订单详情视图
     */
    public static OrderDetailVO from(
            OrderDO orderDO,
            List<OrderItemDO> orderItems
    ) {
        return from(orderDO, null, orderItems);
    }

    /**
     * 从订单对象和用户信息构造订单视图。
     *
     * @param orderDO 订单持久化对象
     * @param userBasicVO 用户基础信息
     * @return 订单视图
     */
    public static OrderDetailVO from(
            OrderDO orderDO,
            UserBasicVO userBasicVO
    ) {
        return from(orderDO, userBasicVO, null);
    }

    /**
     * 从订单、用户和明细数据构造完整详情视图。
     *
     * @param orderDO 订单持久化对象
     * @param userBasicVO 用户基础信息
     * @param orderItems 订单明细快照
     * @return 订单详情视图
     */
    public static OrderDetailVO from(
            OrderDO orderDO,
            UserBasicVO userBasicVO,
            List<OrderItemDO> orderItems
    ) {
        OrderDO order = Objects.requireNonNull(orderDO, "orderDO must not be null");
        List<OrderItemVO> items = null;
        if (orderItems != null) {
            items = new ArrayList<>();
            for (OrderItemDO orderItem : orderItems) {
                items.add(OrderItemVO.from(orderItem));
            }
        }

        return OrderDetailVO.builder()
                .orderId(order.getOrderId())
                .orderNo(order.getOrderNo())
                .userId(order.getUserId())
                .username(userBasicVO == null ? null : userBasicVO.getUsername())
                .windowId(order.getWindowId())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .remark(order.getRemark())
                .pickupCode(order.getPickupCode())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .items(items)
                .build();
    }
}
