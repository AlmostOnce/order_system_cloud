package com.hbue.ordering.order.vo;

import com.hbue.ordering.order.model.OrderDO;
import com.hbue.ordering.user.api.vo.UserBasicVO;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 订单详情视图对象。
 *
 * <p>该对象只用于向前端返回订单数据，
 * 不直接暴露数据库持久化对象 OrderDO。</p>
 *
 * @author order-system
 * @date 2026-09-19
 */
@Getter
@Builder
@ToString
public class OrderDetailVO {

    /**
     * 订单主键。
     */
    private Long orderId;

    /**
     * 订单编号。
     */
    private String orderNo;

    /**
     * 用户 ID。
     */
    private Long userId;

    /**
     * 用户名。
     */
    private String username;

    /**
     * 窗口 ID。
     */
    private Long windowId;

    /**
     * 订单总金额。
     */
    private BigDecimal totalAmount;

    /**
     * 订单状态。
     */
    private String status;

    /**
     * 订单备注。
     */
    private String remark;

    /**
     * 取餐码。
     */
    private String pickupCode;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 修改时间。
     */
    private LocalDateTime updatedAt;

    /**
     * 将订单持久化对象转换为订单详情视图对象。
     *
     * @param orderDO 订单持久化对象
     * @return 订单详情视图对象
     */
    public static OrderDetailVO from(OrderDO orderDO) {
        // 持久化对象不能为空。
        OrderDO validOrder = Objects.requireNonNull(orderDO, "orderDO must not be null");

        // 只复制允许对外返回的订单字段。
        return OrderDetailVO.builder()
                .orderId(validOrder.getOrderId())
                .orderNo(validOrder.getOrderNo())
                .userId(validOrder.getUserId())
                .windowId(validOrder.getWindowId())
                .totalAmount(validOrder.getTotalAmount())
                .status(validOrder.getStatus())
                .remark(validOrder.getRemark())
                .pickupCode(validOrder.getPickupCode())
                .createdAt(validOrder.getCreatedAt())
                .updatedAt(validOrder.getUpdatedAt())
                .build();
    }


    /**
     * 将订单对象和用户对象转换为订单详情视图对象。
     *
     * @param orderDO 订单持久化对象
     * @param userBasicVO 用户基础信息
     * @return 订单详情视图对象
     */
    public static OrderDetailVO from(
            OrderDO orderDO,
            UserBasicVO userBasicVO
    ) {
        // 订单对象不能为空。
        OrderDO validOrder = Objects.requireNonNull(
                orderDO,
                "orderDO must not be null"
        );

        // 用户对象不能为空。
        UserBasicVO validUser = Objects.requireNonNull(
                userBasicVO,
                "userBasicVO must not be null"
        );

        // 组装订单和用户信息。
        return OrderDetailVO.builder()
                .orderId(validOrder.getOrderId())
                .orderNo(validOrder.getOrderNo())
                .userId(validOrder.getUserId())
                .windowId(validOrder.getWindowId())
                .totalAmount(validOrder.getTotalAmount())
                .status(validOrder.getStatus())
                .remark(validOrder.getRemark())
                .pickupCode(validOrder.getPickupCode())
                .createdAt(validOrder.getCreatedAt())
                .updatedAt(validOrder.getUpdatedAt())
                .username(validUser.getUsername())
                .build();
    }
}