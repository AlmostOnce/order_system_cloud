package com.hbue.ordering.product.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量查询指定窗口内可下单菜品及当前价格的服务间请求 DTO。
 *
 * <p>该对象属于商品服务核价接口契约，不是前端订单创建对象；
 * 调用方提供窗口和最多 100 个正整数菜品 ID，商品服务仍会自行核对
 * 窗口、菜品、分类和售卖状态，不能把请求中的 ID 当作可信价格或状态依据。</p>
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

    /** 需要核价的菜品 ID，最多 100 个；元素不能为空且必须为正整数，重复项由服务层拒绝。 */
    @NotEmpty
    @Size(max = MAX_PRODUCT_IDS)
    private List<@NotNull @Positive Long> productIds;
}
