package com.hbue.ordering.order.ranking;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.order.mapper.SalesOutboxMapper;
import com.hbue.ordering.order.mapper.OrderMapper;
import com.hbue.ordering.order.mapper.OrderItemMapper;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.order.model.OrderDO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订单支付销量事件的本地 Outbox 写入器。
 *
 * <p>支付状态与 paid_at 已由订单 CAS 写入；本类在同一订单库事务中重读订单及菜品快照并保存消息。
 * 任一快照不完整或消息无法序列化时抛出异常，让支付状态、支付时间和 Outbox 一起回滚；
 * 因此不会出现支付状态已提交而事件只能靠事后补写的窗口。</p>
 */
@Service
public class SalesOutboxWriter {
    private final SalesOutboxMapper mapper;
    private final ObjectMapper json;
    private final OrderMapper orders;
    private final OrderItemMapper items;

    /** 注入本地订单与明细 Mapper，保证事件快照来自订单服务拥有的事实表。 */
    public SalesOutboxWriter(SalesOutboxMapper mapper, ObjectMapper json, OrderMapper orders, OrderItemMapper items) {
        this.mapper = mapper; this.json = json; this.orders = orders; this.items = items;
    }

    /**
     * 把本次首次支付固化为带菜品明细的 V2 事件。
     *
     * @param orderId 已支付订单内部主键
     * @param paidAt 支付服务持久化的首次成功时间，必须与订单表 paid_at 完全一致
     * @throws IllegalStateException 序列化失败时抛出，触发外层订单事务回滚
     * @throws BusinessException 订单支付事实、窗口或明细缺失时拒绝保存不完整事件
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordPaid(Long orderId, java.time.LocalDateTime paidAt) {
        // 强制加入调用方状态 CAS 的本地事务，防止支付状态提交后 outbox 写入失败。
        OrderDO order = orders.selectById(orderId);
        if (order == null || order.getPaidAt() == null || !order.getPaidAt().equals(paidAt)
                || order.getWindowId() == null || order.getWindowId() <= 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "销量事件缺少一致的订单支付快照");
        }
        var details = items.selectByOrderId(orderId);
        if (details == null || details.isEmpty()) throw new BusinessException(CommonErrorCode.CONFLICT, "已支付订单缺少菜品明细");
        var event = OrderPaidSalesEvent.of(order, details);
        if (!event.valid()) throw new BusinessException(CommonErrorCode.CONFLICT, "已支付订单销量快照格式不正确");
        try {
            if (mapper.insert(event.eventId(), orderId, json.writeValueAsString(event)) != 1) {
                throw new IllegalStateException("支付销量事件保存失败");
            }
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("支付销量事件编码失败", exception);
        }
    }
}
