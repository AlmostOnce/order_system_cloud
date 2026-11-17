package com.hbue.ordering.product.api.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

/** 一行待核价菜品及选项；相同菜品允许不同组合，金额与规格名称必须由商品服务解析。 */
@Data
public class ProductQuoteItemRequest {
    /** 菜品编号；必须属于请求窗口并处于可售状态。 */
    @NotNull @Positive
    private Long productId;
    /** 选择的启用选项编号；最多10组单选，空数组表示不选择任何规格，不接受null或重复编号。 */
    @NotNull @Size(max = 10)
    private List<@NotNull @Positive Long> selectedOptionIds = List.of();
}
