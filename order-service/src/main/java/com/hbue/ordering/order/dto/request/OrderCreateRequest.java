package com.hbue.ordering.order.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 创建订单请求对象。金额由服务端根据当前商品价格计算。
 *
 * @author order-system
 * @date 2026-09-28
 */
@Data
public class OrderCreateRequest {

    /** 每个订单允许提交的最大菜品行数。 */
    public static final int MAX_ITEM_COUNT = 100;

    /** 订单所属窗口 ID。 */
    @NotNull(message = "窗口 ID 不能为空")
    @Positive(message = "窗口 ID 必须大于 0")
    private Long windowId;

    /** 订单菜品清单，菜品 ID 不可重复。 */
    @NotEmpty(message = "订单至少需要一个菜品")
    @Size(max = MAX_ITEM_COUNT, message = "订单菜品不能超过 100 项")
    @Valid
    private List<OrderItemCreateRequest> items;

    /** 订单备注。 */
    @Size(max = 255, message = "订单备注不能超过 255 个字符")
    private String remark;
}
