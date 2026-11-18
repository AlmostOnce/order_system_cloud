package com.hbue.ordering.recommendation.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 统计服务累计销量榜的短期缓存；热门度口径由 statistics-service 保持唯一。
 */
@Data
@TableName("recommendation_hot_products")
public class RecommendationHotProductDO {
    /**
     * 菜品主键。
     */
    @TableId("product_id")
    private Long productId;
    /**
     * 菜品当前窗口主键。
     */
    private Long windowId;
    /**
     * 统计服务定义的累计已支付销量。
     */
    private Long sales;
    /**
     * 统计服务产生的当前榜单排名。
     */
    private Integer rankNo;
    /**
     * 最近一次成功同步时间。
     */
    private LocalDateTime updatedAt;
}
