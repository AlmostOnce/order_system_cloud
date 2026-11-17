package com.hbue.ordering.recommendation.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 当前商品目录的本地只读快照；交易时仍由商品服务重新核验价格和售卖状态。 */
@Data
@TableName("recommendation_product_features")
public class RecommendationProductFeatureDO {
    /** 商品服务菜品主键。 */
    @TableId("product_id") private Long productId;
    /** 当前商品分类主键。 */
    private Long categoryId;
    /** 当前所属窗口主键。 */
    private Long windowId;
    /** 当前所属餐厅主键。 */
    private Long restaurantId;
    /** 当前窗口展示名称。 */
    private String windowName;
    /** 当前分类名称；无分类时为空。 */
    private String categoryName;
    /** 当前菜名文本。 */
    private String productName;
    /** 当前菜品描述文本。 */
    private String description;
    /** 当前商品展示图地址。 */
    private String imageUrl;
    /** 当前基础价；有规格时仅展示为起价，不代表实际组合单价。 */
    private BigDecimal basePrice;
    /** 同分时用商品目录顺序稳定排序。 */
    private Integer sortOrder;
    /** 当前是否配置启用规格组。 */
    private Boolean hasSpecifications;
    /** 当前启用规格组的JSON，仅用于匹配可选口味标签。 */
    private String specificationGroups;
    /** 当前是否仍可推荐；下架商品保留作历史文本画像特征。 */
    private Boolean available;
    /** 商品服务快照更新时间。 */
    private LocalDateTime updatedAt;
}
