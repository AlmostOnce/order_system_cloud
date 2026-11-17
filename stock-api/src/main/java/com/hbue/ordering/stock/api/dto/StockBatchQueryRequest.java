package com.hbue.ordering.stock.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 推荐展示前的有界库存复核请求，不修改库存或创建预留。
 * 每次必须提交1至50个不同的档口与菜品组合，禁止用空条件读取全库。
 * 身份由内部调用令牌校验；菜品是否在售仍由商品域负责，不在库存查询中推断。
 *
 * @param items 本次待复核的组合，元素和两个编号均不能为空且编号必须为正数
 */
public record StockBatchQueryRequest(
        @NotEmpty @Size(max = 50) List<@NotNull @Valid Item> items
) {
    /**
     * 菜品级库存定位键；不同规格共用此键，不接收客户端计算的库存或规格组合库存。
     *
     * @param windowId 商品所属档口编号
     * @param productId 当前菜品编号
     */
    public record Item(@NotNull @Positive Long windowId, @NotNull @Positive Long productId) {}
}
