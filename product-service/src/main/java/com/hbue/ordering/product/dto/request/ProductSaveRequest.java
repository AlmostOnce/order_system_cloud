package com.hbue.ordering.product.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 新建或更新窗口菜品的请求对象。
 *
 * @author order-system
 * @date 2026-09-28
 */
@Data
public class ProductSaveRequest {

    /** 分类 ID；为空表示未分类。 */
    @Positive
    private Long categoryId;

    /** 窗口内菜品编码。 */
    @NotBlank
    @Size(max = 64)
    private String productCode;

    /** 菜品名称。 */
    @NotBlank
    @Size(max = 128)
    private String productName;

    /** 菜品描述；为空表示不填写。 */
    @Size(max = 500)
    private String description;

    /** 菜品图片地址；为空表示不填写。 */
    @Size(max = 512)
    private String imageUrl;

    /** 菜品售价，最多两位小数。 */
    @NotNull
    @DecimalMin(value = "0.00")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal price;

    /** 售卖状态：1 在售，0 下架。 */
    @NotNull
    @Min(0)
    @Max(1)
    private Integer saleStatus;

    /** 菜品排序值。 */
    @NotNull
    @Min(0)
    private Integer sortOrder;
}
