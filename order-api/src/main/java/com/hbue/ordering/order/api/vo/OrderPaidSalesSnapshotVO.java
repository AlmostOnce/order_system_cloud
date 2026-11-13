package com.hbue.ordering.order.api.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单服务提供给受信任统计服务的最小已支付快照。
 *
 * <p>快照仅包含统计销量所需的订单支付时间、窗口和下单菜品原始份数，不包含用户身份、支付密码、
 * 金额或支付凭证；商品名称保留订单创建时的快照，避免目录改名改变历史排行。</p>
 *
 * @param orderId 订单主键，用作统计事件幂等来源
 * @param paidAt 首次支付成功时间，统计月归属依据
 * @param windowId 下单窗口主键
 * @param items 下单时的菜品明细快照
 */
public record OrderPaidSalesSnapshotVO(
        Long orderId,
        LocalDateTime paidAt,
        Long windowId,
        List<Item> items
) {
    /** 单条订单菜品快照，不因后续退款修改原始购买数量。 */
    public record Item(Long itemId, Long productId, String productName, Integer quantity) {
    }
}
