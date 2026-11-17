package com.hbue.ordering.recommendation.service;

import com.hbue.ordering.order.api.recommendation.RecommendationPurchaseSnapshotEvent;

/** 将订单支付和成功退款后的完整源版本快照幂等投影到推荐库。 */
public interface RecommendationPurchaseProjectionService {
    /**
     * 只接受有效的更新订单版本，并在一个本地事务内替换订单头及全部明细。
     *
     * @param event 支付成功或成功退款后生成的订单当前全量快照，含逐itemId退款与规格事实
     */
    void apply(RecommendationPurchaseSnapshotEvent event);
}
