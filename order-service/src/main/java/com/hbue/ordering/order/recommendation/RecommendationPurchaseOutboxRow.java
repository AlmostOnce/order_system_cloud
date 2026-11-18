package com.hbue.ordering.order.recommendation;

import lombok.Data;

/**
 * 推荐待发布行；只在订单服务内部使用，不作为外部API，消息正文含完整不可变购买快照。
 */
@Data
public class RecommendationPurchaseOutboxRow {
    /**
     * 稳定事件键，与源订单及其版本一致。
     */
    private String eventId;
    /**
     * 仅用于校验消息所属订单，不用于跨库查询。
     */
    private Long orderId;
    /**
     * 该事件保存时的源版本，不随订单后续更新变化。
     */
    private Integer orderVersion;
    /**
     * 序列化事件，发布时不得重读订单后覆盖旧事件内容。
     */
    private String payload;
}
