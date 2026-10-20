package com.hbue.ordering.stock.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 库存扣减请求。
 *
 * <p>由 order-service 在下单事务中提交给 stock-service：订单落库后调用，
 * 按 {@code (windowId, productId)} 逐行扣减可售数量。</p>
 *
 * <p>{@code bizRef}（订单号）与业务类型一起构成 {@code stock_flow} 的幂等唯一键，
 * 同一订单重复提交不会重复扣减。库存不足时 stock-service 返回失败，
 * 调用方必须让全局事务回滚，不能忽略失败结果继续落单。</p>
 *
 * @author order-system
 * @date 2026-10-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockDeductRequest {

    /**
     * 业务单据号（订单号），用于幂等记账。
     */
    @NotBlank(message = "业务单据号不能为空")
    private String bizRef;

    /**
     * 档口 ID，订单内所有菜品同属一个档口。
     */
    @NotNull(message = "档口 ID 不能为空")
    private Long windowId;

    /**
     * 待扣减的菜品与数量明细。
     */
    @NotEmpty(message = "扣减明细不能为空")
    @Valid
    private List<StockItemRequest> items;

    /**
     * 操作者 user_id，系统触发可为空。
     */
    private Long operatorId;

    /**
     * 备注，用于流水排查。
     */
    private String remark;
}
