package com.hbue.ordering.statistics.messaging;

import com.hbue.ordering.order.api.vo.OrderPaidSalesSnapshotVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * RocketMQ 已支付事件的统计侧反序列化契约。
 *
 * <p>V1 仅含订单 ID，历史兼容时由订单内部接口补齐；V2 自带完整购买快照，减少新消息消费对订单服务的同步依赖。</p>
 *
 * <p>销量口径：事件在支付成功时投递且每单只投递一次，统计侧只累加原始购买份数（毛销量）；
 * 退款不会产生回冲事件，也不会减少已累计的销量。推荐服务的热榜同步读取同一口径，
 * 因此本事件刻意不携带退款信息，退款口径变更只能由统计服务统一发布新契约。</p>
 */
public record SalesPaidEvent(int version, String eventId, Long orderId, LocalDateTime paidAt,
                             Long windowId, List<Item> items) {
    /**
     * Jackson 读取旧版仅含前三个字段的 JSON 时保留兼容入口。
     */
    public SalesPaidEvent(int version, String eventId, Long orderId) {
        this(version, eventId, orderId, null, null, List.of());
    }

    /**
     * 校验事件版本与稳定事件键；V1 的快照字段必须为空，V2 的每条明细必须完整。
     */
    public boolean valid() {
        if (orderId == null || orderId <= 0 || !("order-paid-sales:" + orderId).equals(eventId)) return false;
        if (version == 1) return paidAt == null && windowId == null && (items == null || items.isEmpty());
        return version == 2 && paidAt != null && windowId != null && windowId > 0
                && items != null && !items.isEmpty()
                && items.stream().allMatch(item -> item != null && item.itemId() != null && item.itemId() > 0
                && item.productId() != null && item.productId() > 0 && item.productName() != null
                && !item.productName().isBlank() && item.productName().length() <= 255
                && item.quantity() != null && item.quantity() > 0);
    }

    /**
     * 把当前契约的字段转换为订单服务可读取的最小快照模型。
     */
    public OrderPaidSalesSnapshotVO snapshot() {
        return new OrderPaidSalesSnapshotVO(orderId, paidAt, windowId,
                items == null ? List.of() : items.stream()
                        .map(item -> new OrderPaidSalesSnapshotVO.Item(item.itemId(), item.productId(), item.productName(), item.quantity()))
                        .toList());
    }

    /**
     * 与订单服务 outbox 一致的单行菜品快照。
     */
    public record Item(Long itemId, Long productId, String productName, Integer quantity) {
    }
}
