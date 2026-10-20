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
 * 库存回补请求。
 *
 * <p>订单取消、超时未支付或退款时由 order-service 提交给 stock-service，
 * 把此前扣减的可售数量加回。</p>
 *
 * <p>回补使用与扣减不同的业务类型记为 {@code ORDER_RESTORE}，
 * 因此同一订单可以「先扣后补」各记一次流水；但回补本身仍受
 * {@code stock_flow} 唯一键约束，重复回补不会重复加库存。</p>
 *
 * @author order-system
 * @date 2026-10-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockRestoreRequest {

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
     * 待回补的菜品与数量明细。
     */
    @NotEmpty(message = "回补明细不能为空")
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
