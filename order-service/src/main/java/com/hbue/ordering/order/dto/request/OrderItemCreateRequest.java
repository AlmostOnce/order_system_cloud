package com.hbue.ordering.order.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 创建订单时提交的一行菜品、规格选项 ID 和购买数量。
 *
 * <p>该请求不包含商品名称或价格，这些数据由订单服务通过商品服务核价取得，
 * 并以快照形式写入订单明细；规格选项 ID 由商品服务校验并重新计算加价，
 * 未配置规格的菜品继续使用空选项列表。</p>
 *
 * @author order-system
 * @date 2026-09-28
 */
@Data
public class OrderItemCreateRequest {

    /**
     * 菜品 ID。
     */
    @NotNull(message = "菜品 ID 不能为空")
    @Positive(message = "菜品 ID 必须大于 0")
    private Long productId;

    /**
     * 购买数量。
     */
    @NotNull(message = "菜品数量不能为空")
    @Positive(message = "菜品数量必须大于 0")
    private Integer quantity;

    /**
     * 顾客在各单选规格组中选择的选项 ID；空列表表示没有可选规格。
     */
    @Size(max = 10, message = "菜品规格选项不能超过 10 项")
    private List<@NotNull(message = "规格选项 ID 不能为空") @Positive(message = "规格选项 ID 必须大于 0") Long> selectedOptionIds;
}
