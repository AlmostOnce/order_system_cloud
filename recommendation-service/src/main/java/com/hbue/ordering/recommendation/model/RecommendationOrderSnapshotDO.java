package com.hbue.ordering.recommendation.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 推荐服务保存的最新订单全量快照头；源订单状态和版本仍归订单服务所有。
 */
@Data
@TableName("recommendation_order_snapshots")
public class RecommendationOrderSnapshotDO {
    /**
     * 订单服务分配的稳定订单主键。
     */
    @TableId("order_id")
    private Long orderId;
    /**
     * 订单所属顾客内部主键，只用于该顾客自己的画像。
     */
    private Long userId;
    /**
     * 下单窗口，用于窗口兴趣与候选范围计算。
     */
    private Long windowId;
    /**
     * 源订单单调版本；旧版本事件不得覆盖退款后的新快照。
     */
    private Integer orderVersion;
    /**
     * 首次支付时间，退款成功不会把购买行为改成退款日期。
     */
    private LocalDateTime paidAt;
    /**
     * 推荐服务接收该源版本的时间。
     */
    private LocalDateTime updatedAt;
}
