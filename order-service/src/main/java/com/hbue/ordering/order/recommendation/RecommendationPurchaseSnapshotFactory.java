package com.hbue.ordering.order.recommendation;

import com.hbue.ordering.order.api.recommendation.RecommendationPurchaseSnapshotEvent;
import com.hbue.ordering.order.model.OrderDO;
import com.hbue.ordering.order.model.OrderItemDO;
import java.util.Comparator;
import java.util.List;

/**
 * 实时Outbox与历史回填共用的购买快照转换，确保同一源版本只有一种字段口径。
 * 不访问其他服务或推测历史规格，只接收调用方在同一数据库事务内读取的订单及其全部明细。
 */
public final class RecommendationPurchaseSnapshotFactory {
    private RecommendationPurchaseSnapshotFactory() { }

    /**
     * 构造已支付订单完整快照；按itemId稳定排序，使重复读取同一事实不会因列表顺序产生冲突。
     *
     * @param order 已支付的源订单，必须包含真实用户、窗口、源版本及首次paidAt
     * @param details 本订单全部明细，保留全额退款行与不同规格拆行
     * @return version=1的推荐快照，eventId绑定源订单和源版本
     * @throws IllegalStateException 源事实缺失、跨订单明细或非法退款份数时拒绝导出，不静默跳过
     */
    public static RecommendationPurchaseSnapshotEvent create(OrderDO order, List<OrderItemDO> details) {
        if (order == null || order.getOrderId() == null || order.getVersion() == null || order.getPaidAt() == null
                || details == null || details.isEmpty()
                || details.stream().anyMatch(item -> item == null || item.getItemId() == null
                        || !order.getOrderId().equals(item.getOrderId()))) {
            throw new IllegalStateException("推荐购买快照缺少一致的订单明细事实");
        }
        var snapshot = details.stream().sorted(Comparator.comparing(OrderItemDO::getItemId)).map(item -> {
            String key = item.getSelectionKeySnapshot() == null || item.getSelectionKeySnapshot().isBlank()
                    ? "base" : item.getSelectionKeySnapshot();
            // 历史结构化选择未知时维持 []；名称与标签不能由当前商品配置反向补造。
            String selection = OrderSpecificationSnapshot.validate(key, item.getSpecificationSelectionSnapshot(), true);
            return new RecommendationPurchaseSnapshotEvent.Item(item.getItemId(), item.getProductId(), key,
                    item.getSpecificationTextSnapshot() == null ? "" : item.getSpecificationTextSnapshot(), selection,
                    item.getQuantity(), item.getRefundedQuantity() == null ? 0 : item.getRefundedQuantity());
        }).toList();
        var event = new RecommendationPurchaseSnapshotEvent(1,
                RecommendationPurchaseSnapshotEvent.eventId(order.getOrderId(), order.getVersion()),
                order.getOrderId(), order.getVersion(), order.getUserId(), order.getWindowId(), order.getPaidAt(), snapshot);
        if (!event.valid()) throw new IllegalStateException("推荐购买快照格式不正确");
        return event;
    }
}
