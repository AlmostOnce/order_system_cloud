package com.hbue.ordering.order.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.core.scope.DataScopeService;
import com.hbue.ordering.common.redis.RedisOperationsService;
import com.hbue.ordering.common.web.audit.ManagementAuditLogger;
import com.hbue.ordering.order.enums.OrderStatus;
import com.hbue.ordering.order.mapper.OrderMapper;
import com.hbue.ordering.order.model.OrderDO;
import com.hbue.ordering.order.service.OrderPersistenceService;
import com.hbue.ordering.order.service.OrderPickupService;
import com.hbue.ordering.order.vo.OrderStatusVO;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import org.springframework.stereotype.Service;

@Service
public class OrderPickupServiceImpl implements OrderPickupService {
    private final OrderMapper orders;
    private final OrderPersistenceService persistence;
    private final DataScopeService scopes;
    private final RedisOperationsService redis;
    private final ManagementAuditLogger audit;

    public OrderPickupServiceImpl(OrderMapper orders, OrderPersistenceService persistence,
            DataScopeService scopes, RedisOperationsService redis, ManagementAuditLogger audit) {
        this.orders = orders; this.persistence = persistence; this.scopes = scopes; this.redis = redis; this.audit = audit;
    }

    @Override
    public String getOwnPickupCode(Long userId, Long orderId) {
        OrderDO order = orders.selectById(orderId);
        if (order == null || !userId.equals(order.getUserId())) throw notFound();
        return persistence.ensurePickupCode(orderId, userId);
    }

    @Override
    public OrderStatusVO verifyPickup(Long operatorId, String pickupCode) {
        if (pickupCode == null || !pickupCode.matches("[0-9]{6}"))
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "请输入六位数字取餐码");
        // 错误码无法定位订单，因此按操作者限制全部尝试；同样覆盖不存在或越权的码。
        Long attempts = redis.increment("order:pickup:operator-attempts:" + operatorId, Duration.ofMinutes(1));
        if (attempts == null || attempts > 30)
            throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS, "核销尝试过于频繁，请一分钟后再试");
        OrderDO order = orders.selectByActivePickupCode(pickupCode);
        if (order == null || !scopes.canAccessWindow(operatorId, order.getWindowId())) throw notFound();
        if (!OrderStatus.WAITING_FOR_PICKUP.getLabel().equals(order.getStatus()) || order.getVersion() == null)
            throw new BusinessException(CommonErrorCode.CONFLICT, "只有待取货订单可以核销，已完成订单不可重复核销");
        String expected = order.getPickupCode();
        if (expected == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), pickupCode.getBytes(StandardCharsets.US_ASCII))) {
            audit.record(operatorId, "ORDER_PICKUP_VERIFY", "ORDER", order.getOrderId(), order.getStatus(), order.getStatus(), false);
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "取餐码不正确");
        }
        if (!persistence.completeWithPickupCode(order.getOrderId(), order.getVersion(), pickupCode))
            throw new BusinessException(CommonErrorCode.CONFLICT, "订单状态已变化，请核对后重试");
        audit.record(operatorId, "ORDER_PICKUP_VERIFY", "ORDER", order.getOrderId(), order.getStatus(), OrderStatus.COMPLETED.getLabel(), true);
        return OrderStatusVO.builder().orderId(order.getOrderId()).status(OrderStatus.COMPLETED.getLabel()).build();
    }

    private BusinessException notFound() { return new BusinessException(CommonErrorCode.NOT_FOUND, "取餐码无效或订单不可核销"); }
}
