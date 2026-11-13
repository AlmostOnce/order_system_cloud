package com.hbue.ordering.order.ranking;

import com.hbue.ordering.order.model.OrderDO;
import com.hbue.ordering.order.model.OrderItemDO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单服务发布给统计服务的销量事件。
 *
 * <p>V2 携带订单在支付时固化的 paid_at、窗口和菜品原始购买快照，使消费者不必逐笔查询订单库；
 * V1 的三字段构造器只用于读取历史 outbox，统计服务会通过受内部令牌保护的只读接口补齐事实。
 * 事件不含用户身份、金额、支付密码或支付凭证。</p>
 */
public record OrderPaidSalesEvent(int version, String eventId, Long orderId, LocalDateTime paidAt,
                                  Long windowId, List<Item> items) {
    /**
     * 兼容反序列化及已有 V1 outbox 的构造形式；新写入必须使用 {@link #of(OrderDO, List)}。
     */
    public OrderPaidSalesEvent(int version, String eventId, Long orderId) {
        this(version, eventId, orderId, null, null, List.of());
    }

    /** 创建兼容旧版 ID-only 消息的事件对象。 */
    public static OrderPaidSalesEvent of(Long orderId) {
        return new OrderPaidSalesEvent(1, "order-paid-sales:" + orderId, orderId);
    }

    /** 从订单库同事务读到的支付事实和菜品快照创建完整事件。 */
    public static OrderPaidSalesEvent of(OrderDO order, List<OrderItemDO> orderItems) {
        List<Item> snapshot = orderItems.stream()
                .map(item -> new Item(item.getItemId(), item.getProductId(), item.getProductNameSnapshot(), item.getQuantity()))
                .toList();
        return new OrderPaidSalesEvent(2, "order-paid-sales:" + order.getOrderId(), order.getOrderId(),
                order.getPaidAt(), order.getWindowId(), snapshot);
    }

    /** 校验事件版本、稳定幂等键及 V2 快照字段，拒绝把损坏消息写入统计事实表。 */
    public boolean valid() {
        if (orderId == null || orderId <= 0 || !("order-paid-sales:" + orderId).equals(eventId)) return false;
        if (version == 1) return paidAt == null && windowId == null && (items == null || items.isEmpty());
        if (version != 2 || paidAt == null || windowId == null || windowId <= 0 || items == null || items.isEmpty()) return false;
        return items.stream().allMatch(item -> item != null && item.itemId() != null && item.itemId() > 0
                && item.productId() != null && item.productId() > 0 && item.productName() != null
                && !item.productName().isBlank() && item.productName().length() <= 255
                && item.quantity() != null && item.quantity() > 0);
    }

    /** 不受后续退款改变的单行订单菜品购买快照。 */
    public record Item(Long itemId, Long productId, String productName, Integer quantity) {
    }
}
