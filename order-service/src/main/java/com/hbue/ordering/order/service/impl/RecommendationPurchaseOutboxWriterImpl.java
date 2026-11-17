package com.hbue.ordering.order.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.order.api.recommendation.RecommendationPurchaseSnapshotEvent;
import com.hbue.ordering.order.mapper.OrderItemMapper;
import com.hbue.ordering.order.mapper.OrderMapper;
import com.hbue.ordering.order.mapper.RecommendationPurchaseOutboxMapper;
import com.hbue.ordering.order.recommendation.RecommendationPurchaseSnapshotFactory;
import com.hbue.ordering.order.service.RecommendationPurchaseOutboxWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 支付确认及成功退款的推荐事件写入器，强制加入源订单本地事务，不在此处访问MQ。
 * 写入或校验失败向外抛出，使源状态、退款计数与Outbox一起回滚；异步派发失败则不会重做资金操作。
 */
@Service
public class RecommendationPurchaseOutboxWriterImpl implements RecommendationPurchaseOutboxWriter {
    /** 订单、全部明细和Outbox均在order_db中，重读可见本事务刚刚完成的变更。 */
    private final OrderMapper orders;
    private final OrderItemMapper items;
    private final RecommendationPurchaseOutboxMapper outbox;
    /** 使用Spring提供的JSON配置，LocalDateTime编码与项目消息协议保持一致。 */
    private final ObjectMapper json;

    /** 注入订单事实及事件持久化能力，不接受客户端事件或个人身份字段。 */
    public RecommendationPurchaseOutboxWriterImpl(OrderMapper orders, OrderItemMapper items,
            RecommendationPurchaseOutboxMapper outbox, ObjectMapper json) {
        this.orders = orders; this.items = items; this.outbox = outbox; this.json = json;
    }

    /**
     * 保存本次源版本的全量明细，paidAt为首次支付时间；已成功退款的行也保留，以便消费者撤销净份数。
     *
     * @param orderId 已完成支付或成功退款状态更新的订单主键
     * @throws IllegalStateException 缺失事实、同版本冲突或编码失败时抛出，触发调用方事务回滚
     */
    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public void recordCurrent(Long orderId) {
        var order = orders.selectById(orderId);
        var details = items.selectByOrderId(orderId);
        // 与历史回填共用转换，防止同版消息因两条路径的退款/规格口径不同而发生冲突。
        var event = RecommendationPurchaseSnapshotFactory.create(order, details);
        try {
            outbox.insertIfAbsent(event.eventId(), orderId, order.getVersion(), json.writeValueAsString(event));
            var saved = outbox.find(event.eventId());
            // JSON列可能调整属性顺序，按事件值比较而非原始文本；重复写同版允许，正文冲突则拒绝提交。
            if (saved == null || !orderId.equals(saved.getOrderId()) || !order.getVersion().equals(saved.getOrderVersion())
                    || !event.equals(json.readValue(saved.getPayload(), RecommendationPurchaseSnapshotEvent.class))) {
                throw new IllegalStateException("同一订单版本的推荐快照发生冲突");
            }
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("推荐购买事件编码或回读失败", exception);
        }
    }
}
