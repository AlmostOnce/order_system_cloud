package com.hbue.ordering.product.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 整份替换菜品规格；省略的旧组和选项停用，空 groups 表示清除全部配置，不接受 null。
 */
@Data
public class ProductSpecificationsSaveRequest {
    /**
     * 本次完整启用组列表；最多10组，组名称和编号的唯一性由服务校验。
     */
    @NotNull
    @Size(max = 10)
    private List<@NotNull @Valid Group> groups;

    /**
     * 一组单选配置；新建时不传编号，修改时必须传回原编号。
     */
    @Data
    public static class Group {
        /**
         * 可选的已有组编号，必须属于目标菜品。
         */
        @Positive
        private Long groupId;
        /**
         * 组名称；去除首尾空白后校验同一菜品内唯一。
         */
        @NotBlank
        @Size(max = 50)
        private String name;
        /**
         * 必选标志；必选组必须包含至少一个零加价项。
         */
        @NotNull
        private Boolean required;
        /**
         * 非负排序值。
         */
        @NotNull
        @Min(0)
        private Integer sortOrder = 0;
        /**
         * 启用选项的完整列表；每组1至20项。
         */
        @NotEmpty
        @Size(max = 20)
        private List<@NotNull @Valid Option> options;
    }

    /**
     * 单个选项配置；已有选项只能在原组内更新，不能借替换接口移动到其他组。
     */
    @Data
    public static class Option {
        /**
         * 可选的已有选项编号。
         */
        @Positive
        private Long optionId;
        /**
         * 选项名；去空白后同组唯一。
         */
        @NotBlank
        @Size(max = 50)
        private String name;
        /**
         * 加价不得为负，最多8位整数和2位小数，与 DECIMAL(10,2) 一致。
         */
        @NotNull
        @DecimalMin("0.00")
        @Digits(integer = 8, fraction = 2)
        private BigDecimal additionalPrice;
        /**
         * 非负排序值。
         */
        @NotNull
        @Min(0)
        private Integer sortOrder = 0;
    }
}
