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
    private static final java.security.SecureRandom PICKUP_RANDOM = new java.security.SecureRandom();

    /** 兼容已有未生成取餐码的已支付订单，并发读取不会覆盖已签发的码。 */
    @Override
    @Transactional
    public String ensurePickupCode(Long orderId, Long userId) {
        boolean assigned = false;
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = String.format(java.util.Locale.ROOT, "%06d", PICKUP_RANDOM.nextInt(1_000_000));
            try {
                orderMapper.assignPickupCodeIfMissing(orderId, userId, code);
                assigned = true;
                break;
            } catch (org.springframework.dao.DuplicateKeyException collision) {
                // 活跃取餐码唯一索引解决并发碰撞；不覆盖其他订单的码。
            }
        }
        if (!assigned) throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "取餐码生成繁忙，请稍后重试");
        OrderDO current = orderMapper.selectById(orderId);
        if (current == null || !userId.equals(current.getUserId()))
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "订单不存在");
        OrderStatus status = OrderStatus.findByLabel(current.getStatus());
        if (status != OrderStatus.PAID && status != OrderStatus.PREPARING && status != OrderStatus.WAITING_FOR_PICKUP)
            throw new BusinessException(CommonErrorCode.CONFLICT, "当前订单不能获取取餐码");
        if (current.getPickupCode() == null || !current.getPickupCode().matches("[0-9]{6}"))
            throw new BusinessException(CommonErrorCode.CONFLICT, "取餐码暂不可用，请联系窗口处理");
        return current.getPickupCode();
    }

    /** 凭据消费、完成状态和通知 outbox 在同一本地事务提交。 */
    @Override
    @Transactional
    public boolean completeWithPickupCode(Long orderId, Integer expectedVersion, String code) {
        boolean changed = orderMapper.completeWithPickupCode(orderId, expectedVersion, code) == 1;
        if (changed) recordStatusNotification(orderId, expectedVersion, OrderStatus.COMPLETED.getLabel());
        return changed;
    }

    private void recordStatusNotification(Long orderId, Integer expectedVersion, String targetStatus) {
        if (!notificationsEnabled || OrderStatus.CANCELLED.getLabel().equals(targetStatus)) return;
        OrderDO current = orderMapper.selectById(orderId);
        if (current == null || current.getUserId() == null || current.getVersion() == null
                || current.getVersion() != expectedVersion + 1 || !targetStatus.equals(current.getStatus()))
            throw new IllegalStateException("订单通知事件缺少有效订单快照");
        notificationOutbox.record(OrderNotificationEvent.of(orderId, current.getVersion(), current.getUserId(), targetStatus));
    }

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
        if (changed) recordStatusNotification(orderId, expectedVersion, targetStatus);
        return changed;
    }
}
