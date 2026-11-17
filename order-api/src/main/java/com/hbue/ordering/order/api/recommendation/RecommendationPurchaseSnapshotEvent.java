package com.hbue.ordering.order.api.recommendation;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 推荐专用完整订单购买快照，首次支付和每次成功退款后由订单库同事务生成。
 * 源订单版本让消费者丢弃重复/较旧快照；退款快照先到也能独立恢复净购买，不依赖先收到支付事件。
 * 不携带金额、支付凭据、取餐码或退款原因，不改变统计服务的累计销量契约。
 *
 * @param version 消息协议版本，当前固定为1，升级字段语义时由生产者消费者协调
 * @param eventId 稳定事件键 recommendation-purchase:订单ID:源版本
 * @param orderId 源订单内部主键
 * @param orderVersion 本次状态/退款变更完成后重读的订单版本
 * @param userId 订单事实中保存的内部用户编号，不由客户端指定
 * @param windowId 下单窗口
 * @param paidAt 首次成功支付时间，退款不得改写
 * @param items 订单当前全部明细，保持规格拆行和每行累计成功退款份数
 */
public record RecommendationPurchaseSnapshotEvent(int version, String eventId, Long orderId, Integer orderVersion,
        Long userId, Long windowId, LocalDateTime paidAt, List<Item> items) {

    /** 稳定键绑定源订单版本；多个发布实例或Broker重投不会产生另一份购买事实。 */
    public static String eventId(Long orderId, Integer orderVersion) {
        return "recommendation-purchase:" + orderId + ":" + orderVersion;
    }

    /**
     * 校验最小事实边界；结构化JSON内容由生产者和消费者分别解析校验。
     * 不允许重复明细或超额退款，避免损坏事实在消费端被静默截断为看似正常的兴趣。
     */
    public boolean valid() {
        if (version != 1 || orderId == null || orderId <= 0 || orderVersion == null || orderVersion < 0
                || !eventId(orderId, orderVersion).equals(eventId)
                || userId == null || userId <= 0 || windowId == null || windowId <= 0
                || paidAt == null || items == null || items.isEmpty() || items.size() > 100) return false;
        Set<Long> ids = new HashSet<>();
        return items.stream().allMatch(item -> item != null && item.itemId() != null && item.itemId() > 0
                && ids.add(item.itemId()) && item.productId() != null && item.productId() > 0
                && item.selectionKey() != null && !item.selectionKey().isBlank()
                && item.specificationText() != null && item.specificationSelectionSnapshot() != null
                && !item.specificationSelectionSnapshot().isBlank()
                && item.purchasedQuantity() != null && item.purchasedQuantity() > 0
                && item.refundedQuantity() != null && item.refundedQuantity() >= 0
                && item.refundedQuantity() <= item.purchasedQuantity());
    }

    /**
     * 单个规格明细的购买事实；推荐端应先逐行扣成功退款，再按订单/菜品求和，最后应用单订单兴趣上限。
     * 规格JSON保留历史组/选项编号及当时名称；旧明细为 []，不推测其历史口味。
     */
    public record Item(Long itemId, Long productId, String selectionKey, String specificationText,
            String specificationSelectionSnapshot, Integer purchasedQuantity, Integer refundedQuantity) { }
}
