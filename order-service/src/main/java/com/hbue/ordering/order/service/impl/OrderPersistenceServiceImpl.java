package com.hbue.ordering.order.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.order.mapper.OrderItemMapper;
import com.hbue.ordering.order.mapper.OrderMapper;
import com.hbue.ordering.order.model.OrderDO;
import com.hbue.ordering.order.model.OrderItemDO;
import com.hbue.ordering.order.service.OrderPersistenceService;
import com.hbue.ordering.order.ranking.SalesOutboxWriter;
import com.hbue.ordering.order.service.CouponConsumeOutboxWriter;
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
        // 全部份数已经批准退款时，旧码也不能继续展示；真正核销还受SQL条件及版本保护。
        if (orderItemMapper.selectByOrderId(orderId).stream().noneMatch(item ->
                item.getQuantity() > (item.getRefundApprovedQuantity() == null ? 0 : item.getRefundApprovedQuantity())))
            throw new BusinessException(CommonErrorCode.CONFLICT, "全部菜品已进入退款，无需取餐");
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

    private final com.hbue.ordering.order.service.OrderTimeoutOutboxService timeoutOutbox;

    /** 订单表 Mapper。 */
    private final OrderMapper orderMapper;

    /** 订单明细表 Mapper。 */
    private final OrderItemMapper orderItemMapper;
    private final SalesOutboxWriter salesOutbox;
    private final CouponConsumeOutboxWriter couponConsumeOutbox;
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
     * @param salesOutbox 支付成功销量事件 Outbox 写入器
     * @param timeoutOutbox 待支付订单超时关单事件 Outbox 写入器
     * @param couponConsumeOutbox 支付成功券核销事件 Outbox 写入器
     */
    public OrderPersistenceServiceImpl(
            OrderMapper orderMapper,
            OrderItemMapper orderItemMapper,
            SalesOutboxWriter salesOutbox,
            com.hbue.ordering.order.service.OrderTimeoutOutboxService timeoutOutbox,
            CouponConsumeOutboxWriter couponConsumeOutbox
    ) {
        this.timeoutOutbox=timeoutOutbox;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.salesOutbox = salesOutbox;
        this.couponConsumeOutbox = couponConsumeOutbox;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public OrderDO saveOrderWithItems(
            OrderDO order,
            List<OrderItemDO> items
    ) {
        if(order.getCreatedAt()==null)order.setCreatedAt(java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")));
        if(order.getExpiresAt()==null)order.setExpiresAt(order.getCreatedAt().plusMinutes(15));
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

        timeoutOutbox.record(com.hbue.ordering.order.dto.OrderTimeoutEvent.of(order.getOrderId(),
                com.hbue.ordering.order.service.OrderPaymentDeadline.epochMillis(order)));
        return order;
    }

    /**
     * 在订单本地事务中写入订单级优惠和逐行分摊；金额条件或任一行更新失败都会回滚全部快照。
     * @param order 已保存并绑定券 ID 的订单金额快照
     * @param items 已计算分摊金额且具有数据库主键的订单行
     * @throws BusinessException 参数不完整、订单不匹配或任一明细保存失败时回滚本地事务
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyCouponSnapshot(OrderDO order, List<OrderItemDO> items) {
        if (order == null || order.getOrderId() == null || order.getUserCouponId() == null
                || order.getTotalAmount() == null || order.getDiscountAmount() == null
                || order.getPayableAmount() == null || items == null || items.isEmpty()) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "优惠券订单快照不完整");
        }
        if (orderMapper.applyCouponSnapshot(order.getOrderId(), order.getUserCouponId(),
                order.getCouponTitleSnapshot(), order.getTotalAmount(), order.getDiscountAmount(),
                order.getPayableAmount()) != 1) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "订单金额已变化，无法应用优惠券");
        }
        for (OrderItemDO item : items) {
            if (item == null || item.getItemId() == null || item.getLineAmount() == null
                    || item.getLineDiscountAmount() == null || item.getLinePayableAmount() == null) {
                throw new BusinessException(CommonErrorCode.CONFLICT, "订单明细金额已变化，无法应用优惠券");
            }
            // 未分到优惠的行仍保留初始的“优惠为零、实付等于原价”快照；跳过无变化 UPDATE，
            // 避免 MySQL 按 changed rows 返回 0 而把合法的低额券误判成并发冲突。
            if (item.getLineDiscountAmount().signum() == 0
                    && item.getLinePayableAmount().compareTo(item.getLineAmount()) == 0) continue;
            if (orderItemMapper.applyCouponSnapshot(item.getItemId(), order.getOrderId(),
                    item.getLineAmount(), item.getLineDiscountAmount(), item.getLinePayableAmount()) != 1) {
                throw new BusinessException(CommonErrorCode.CONFLICT, "订单明细金额已变化，无法应用优惠券");
            }
        }
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
        if (OrderStatus.PAID.getLabel().equals(targetStatus)) {
            // 支付迁移必须同时写入支付服务确认的 paid_at 和销量 outbox，禁止走不含支付事实的通用状态更新。
            throw new IllegalArgumentException("订单支付状态必须通过 markPaidIfUnchanged 写入");
        }
        // 仅当旧状态与版本号同时匹配才执行迁移；受影响行数为 0 表示状态已被其他请求改变。
        boolean changed = orderMapper.updateStatusIfVersionMatches(
                orderId,
                expectedStatus,
                expectedVersion,
                targetStatus
        ) == 1;
        // 取消订单在 Seata 全局事务中，由单独的 TCC 确认阶段生成通知。
        if (changed) recordStatusNotification(orderId, expectedVersion, targetStatus);
        return changed;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public boolean markPaidIfUnchanged(Long orderId, Integer expectedVersion, java.time.LocalDateTime paidAt) {
        if (orderId == null || orderId <= 0 || expectedVersion == null || paidAt == null) {
            throw new IllegalArgumentException("支付确认缺少订单版本或支付成功时间");
        }
        // paid_at、状态及乐观锁版本必须由同一条 CAS SQL 固定，避免两次提交产生错误月份或孤立事件。
        boolean changed = orderMapper.markPaidIfVersionMatches(orderId, expectedVersion, paidAt) == 1;
        if (!changed) return false;

        // outbox writer 使用 MANDATORY 加入当前本地事务；任一快照或事件写入失败都会回滚支付状态。
        salesOutbox.recordPaid(orderId, paidAt);
        // 核销事件在本地订单事务内可靠落库；后续 Broker 故障由独立发布任务重试，不阻塞支付确认。
        couponConsumeOutbox.recordPaid(orderId);
        recordStatusNotification(orderId, expectedVersion, OrderStatus.PAID.getLabel());
        return true;
    }
}
