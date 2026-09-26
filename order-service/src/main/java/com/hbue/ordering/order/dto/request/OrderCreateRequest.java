package com.hbue.ordering.order.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 创建订单请求对象。
 *
 * <p>该对象只接收客户端允许提交的订单字段。
 * 用户 ID 从当前登录用户的 JWT 中获取，
 * 不允许客户端直接提交。</p>
 *
 * @author order-system
 * @date 2026-09-21
 */
@Data
public class OrderCreateRequest {

    /**
     * 窗口 ID。
     */
    @NotNull(message = "窗口 ID 不能为空")
    @Positive(message = "窗口 ID 必须大于 0")
    private Long windowId;

    /**
     * 订单总金额。
     */
    @NotNull(message = "订单金额不能为空")
    @DecimalMin(value = "0.01", message = "订单金额必须大于 0")
    private BigDecimal totalAmount;

    /**
     * 订单备注。
     */
    @Size(max = 255, message = "订单备注不能超过 255 个字符")
    private String remark;
}