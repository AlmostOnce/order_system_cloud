package com.hbue.ordering.product.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建或更新窗口分类的请求对象。
 *
 * @author order-system
 * @date 2026-09-28
 */
@Data
public class ProductCategorySaveRequest {

    /** 分类名称。 */
    @NotBlank
    @Size(max = 64)
    private String categoryName;

    /** 分类排序值。 */
    @NotNull
    @Min(0)
    private Integer sortOrder;

    /** 分类状态：1 启用，0 停用。 */
    @NotNull
    @Min(0)
    @Max(1)
    private Integer status;
}
