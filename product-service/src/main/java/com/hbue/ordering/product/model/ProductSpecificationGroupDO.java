package com.hbue.ordering.product.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 菜品规格组持久化对象；仅映射商品库，权限、完整替换和核价规则由服务层处理。
 */
@Data
@TableName("product_specification_groups")
public class ProductSpecificationGroupDO {
    /**
     * 数据库自增编号；已存在记录更新时保持不变，以便购物车继续核验原选择。
     */
    @TableId(value = "group_id", type = IdType.AUTO)
    private Long groupId;
    /**
     * 所属菜品；规格不能跨菜品移动。
     */
    @TableField("product_id")
    private Long productId;

    /**
     * 组名；同一菜品的启用组中唯一。
     */
    @TableField("name")
    private String name;

    /**
     * 是否必选；必选组必须提供一个零加价选项。
     */
    @TableField("is_required")
    private Boolean required;

    /**
     * 展示顺序；相同值按组编号稳定排序。
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * 启用状态：1启用、0停用；移除时保留历史记录。
     */
    @TableField("status")
    private Integer status;
}
