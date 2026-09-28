package com.hbue.ordering.product.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量查询窗口内可下单菜品及当前价格的请求。
 *
 * @author order-system
 * @date 2026-09-28
 */
@Data
public class ProductQuoteRequest {

    /** 单次核价允许的最大菜品数。 */
    public static final int MAX_PRODUCT_IDS = 100;

    /** 请求菜品所属窗口 ID。 */
    @NotNull
    @Positive
    private Long windowId;

    /** 需要核价的菜品 ID，最多 100 个且不能重复。 */
    @NotEmpty
    @Size(max = MAX_PRODUCT_IDS)
    private List<@NotNull @Positive Long> productIds;
}
