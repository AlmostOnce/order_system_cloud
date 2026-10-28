package com.hbue.ordering.order.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.order.mapper.OrderItemMapper;
import com.hbue.ordering.order.mapper.OrderMapper;
import com.hbue.ordering.order.model.OrderDO;
import com.hbue.ordering.order.model.OrderItemDO;
import com.hbue.ordering.order.service.OrderPersistenceService;
import com.hbue.ordering.order.ranking.SalesOutboxWriter;
import com.hbue.ordering.order.notification.OrderNotificationEvent;
import com.hbue.ordering.order.notification.OrderNotificationOutboxWriter;
import com.hbue.ordering.order.enums.OrderStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 订单及其明细的原子持久化服务实现。
 *
 * @author order-system
 * @date 2026-09-28
 */
@Service
public class OrderPersistenceServiceImpl implements OrderPersistenceService {

    /** 订单表 Mapper。 */
    private final OrderMapper orderMapper;

    /** 订单明细表 Mapper。 */
    private final OrderItemMapper orderItemMapper;
    private final SalesOutboxWriter salesOutbox;
    private OrderNotificationOutboxWriter notificationOutbox;
    @Value("${notification.order-events.enabled:false}")
    private boolean notificationsEnabled;
    @Autowired
    void setNotificationOutbox(OrderNotificationOutboxWriter writer) { this.notificationOutbox = writer; }

    /**
     * 创建订单原子持久化服务实现。
     *
     * @param orderMapper 订单表 Mapper
     * @param orderItemMapper 订单明细表 Mapper
     */
    public OrderPersistenceServiceImpl(
            OrderMapper orderMapper,
            OrderItemMapper orderItemMapper,
            SalesOutboxWriter salesOutbox
    ) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.salesOutbox = salesOutbox;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public OrderDO saveOrderWithItems(
            OrderDO order,
            List<OrderItemDO> items
    ) {
        // 先插入订单主记录以取得数据库生成的主键；事务保证后续失败时主记录也回滚。
        if (orderMapper.insert(order) != 1) {
            throw new BusinessException(
                    CommonErrorCode.INTERNAL_ERROR,
                    "订单创建失败"
            );
        }

        // 明细统一关联新生成的订单 ID；任何一行保存失败都会抛异常并回滚整个订单事务。
        for (OrderItemDO item : items) {
            item.setOrderId(order.getOrderId());
            if (orderItemMapper.insert(item) != 1) {
                throw new BusinessException(
                        CommonErrorCode.INTERNAL_ERROR,
                        "订单明细保存失败"
                );
            }
        }

        return order;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public boolean updateStatusIfUnchanged(
            Long orderId,
            String expectedStatus,
            Integer expectedVersion,
            String targetStatus
    ) {
        // 仅当旧状态与版本号同时匹配才执行迁移；受影响行数为 0 表示状态已被其他请求改变。
        boolean changed = orderMapper.updateStatusIfVersionMatches(
                orderId,
                expectedStatus,
                expectedVersion,
                targetStatus
        ) == 1;
        if (changed && OrderStatus.PAID.getLabel().equals(targetStatus)) salesOutbox.recordPaid(orderId);
        // 取消订单在 Seata 全局事务中，由单独的 TCC 确认阶段生成通知。
        if (changed && notificationsEnabled && !OrderStatus.CANCELLED.getLabel().equals(targetStatus)) {
            OrderDO current = orderMapper.selectById(orderId);
            if (current == null || current.getUserId() == null || current.getVersion() == null
                    || current.getVersion() != expectedVersion + 1 || !targetStatus.equals(current.getStatus()))
                throw new IllegalStateException("订单通知事件缺少有效订单快照");
            notificationOutbox.record(OrderNotificationEvent.of(orderId, current.getVersion(), current.getUserId(), targetStatus));
        }
        return changed;
    }
}
