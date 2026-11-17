package com.hbue.ordering.product.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量查询指定窗口内可下单菜品及当前价格的服务间请求 DTO。
 *
 * <p>该对象属于商品服务核价接口契约，不是前端订单创建对象；
 * 调用方提供窗口和最多100行菜品选项，旧调用方仍可提交菜品ID，商品服务仍会自行核对
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

    /** 兼容旧调用方的无规格菜品编号；与items只允许填一个，重复菜品由服务层拒绝。 */
    @Size(max = MAX_PRODUCT_IDS)
    private List<@NotNull @Positive Long> productIds;
    /** 新版逐行核价请求；最多100行，同菜不同规格允许分行，相同规范化选择键不允许重复。 */
    @Size(max = MAX_PRODUCT_IDS)
    private List<@NotNull @Valid ProductQuoteItemRequest> items;
}
