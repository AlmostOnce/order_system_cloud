package com.hbue.ordering.order.service;

import com.hbue.ordering.order.api.recommendation.RecommendationPurchaseSnapshotPageVO;

/**
 * 推荐服务读取订单权威历史购买快照的内部查询契约。
 */
public interface RecommendationPurchaseSnapshotQueryService {
    /**
     * 按订单主键有界导出已支付订单；不接受用户筛选或修改订单事实。
     *
     * @param afterOrderId 上一页末尾订单主键，首屏为0
     * @param limit        每页订单数量，范围为1至100
     * @return 订单版本、全部明细和下一游标构成的完整快照页
     */
    RecommendationPurchaseSnapshotPageVO page(long afterOrderId, int limit);
}
