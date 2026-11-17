package com.hbue.ordering.product.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;

/** 菜品规格选项持久化对象；仅映射商品库，权限、完整替换和核价规则由服务层处理。 */
@Data
@TableName("product_specification_options")
public class ProductSpecificationOptionDO {
    /** 数据库自增编号；已存在记录更新时保持不变，以便购物车继续核验原选择。 */
    @TableId(value = "option_id", type = IdType.AUTO)
    private Long optionId;
    /** 所属规格组；选项不能跨组移动。 */
    @TableField("group_id")
    private Long groupId;

    /** 选项名；同组启用选项中唯一。 */
    @TableField("name")
    private String name;

    /** 非负两位小数加价；由核价服务累加，不能由客户端覆盖。 */
    @TableField("additional_price")
    private BigDecimal additionalPrice;

    /** 展示顺序；相同值按选项编号稳定排序。 */
    @TableField("sort_order")
    private Integer sortOrder;

    /** 启用状态：1启用、0停用；移除不物理删除。 */
    @TableField("status")
    private Integer status;
}
