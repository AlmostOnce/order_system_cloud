package com.hbue.ordering.recommendation.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 推荐购买事实的订单明细投影，以 itemId 保留同菜不同规格及退款定位。
 */
@Data
@TableName("recommendation_order_items")
public class RecommendationOrderItemDO {
    /**
     * 订单服务源明细主键，也是退款数量归属的唯一定位。
     */
    @TableId("item_id")
    private Long itemId;
    /**
     * 所属源订单主键。
     */
    private Long orderId;
    /**
     * 推荐算法去重及学习使用的菜品主键，不按规格拆商品。
     */
    private Long productId;
    /**
     * 下单时规范化规格组合键，历史无快照订单使用base。
     */
    private String selectionKey;
    /**
     * 下单时供顾客查看的规格文字快照。
     */
    private String specificationTextSnapshot;
    /**
     * 下单时服务端核验的结构化规格选择JSON；历史无数据时为空。
     */
    private String specificationSelectionSnapshot;
    /**
     * 已支付订单原购买数量。
     */
    private Integer purchasedQuantity;
    /**
     * 资金及必要库存步骤完成后确认撤销的数量。
     */
    private Integer refundedQuantity;
}
