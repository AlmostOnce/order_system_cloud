package com.hbue.ordering.product.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 菜单和管理读取共用的启用规格视图；按组和选项排序返回，空配置始终使用空数组。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductSpecificationGroupVO {
    /**
     * 组编号；管理更新时带回该值可保持现有组身份。
     */
    private Long groupId;
    /**
     * 规格组显示名，例如份量。
     */
    private String name;
    /**
     * 是否必须选择本组一个选项。
     */
    private Boolean required;
    /**
     * 组展示顺序，编号用于相同排序值的稳定排序。
     */
    private Integer sortOrder;
    /**
     * 该组启用选项，按排序值及编号升序排列。
     */
    private List<Option> options = List.of();

    /**
     * 单个启用选项；价格为在基础售价之上追加的金额。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Option {
        /**
         * 选项编号；学生只提交此编号，名称和金额由服务端读取。
         */
        private Long optionId;
        /**
         * 选项展示名。
         */
        private String name;
        /**
         * 非负加价金额，单位为元。
         */
        private BigDecimal additionalPrice;
        /**
         * 选项展示顺序。
         */
        private Integer sortOrder;
    }
}
