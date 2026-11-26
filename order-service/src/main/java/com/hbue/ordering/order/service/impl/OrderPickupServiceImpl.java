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
import com.hbue.ordering.order.util.PickupQrCodeRenderer;
import com.hbue.ordering.order.vo.OrderStatusVO;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;

/**
 * 取餐码生成与核销的实现。
 *
 * <p>核销流程固定为：按操作者做频控 → 按取餐码定位订单 → 校验操作者窗口范围 → 校验订单状态
 * → 带版本条件的完成更新。任一步不满足都返回统一的"取餐码无效或订单不可核销"，
 * 避免通过错误提示差异枚举有效取餐码。</p>
 *
 * <p>频控键按操作者而非 IP 计数：取餐码只有 6 位，必须限制单个账号的尝试次数才能防止穷举。</p>
 *
 * @author order-system
 * @date 2026-10-18
 */
@Service
public class OrderPickupServiceImpl implements OrderPickupService {

    /**
     * 订单查询。
     */
    private final OrderMapper orders;

    /**
     * 取餐码生成与状态推进的持久化服务。
     */
    private final OrderPersistenceService persistence;

    /**
     * 窗口数据范围判定，确保店员只能核销自己档口的订单。
     */
    private final DataScopeService scopes;

    /**
     * Redis 频控计数。
     */
    private final RedisOperationsService redis;

    /**
     * 管理操作审计日志。
     */
    private final ManagementAuditLogger audit;

    /**
     * 创建取餐服务。
     *
     * @param orders      订单查询
     * @param persistence 取餐码与状态持久化
     * @param scopes      窗口数据范围判定
     * @param redis       频控计数
     * @param audit       审计日志
     */
    public OrderPickupServiceImpl(OrderMapper orders, OrderPersistenceService persistence,
                                  DataScopeService scopes, RedisOperationsService redis, ManagementAuditLogger audit) {
        this.orders = orders;
        this.persistence = persistence;
        this.scopes = scopes;
        this.redis = redis;
        this.audit = audit;
    }

    @Override
    public String getOwnPickupCode(Long userId, Long orderId) {
        OrderDO order = orders.selectById(orderId);
        if (order == null || !userId.equals(order.getUserId())) throw notFound();
        return persistence.ensurePickupCode(orderId, userId);
    }

    @Override
    public String getOwnPickupQrCode(Long userId, Long orderId) {
        // 复用取餐码入口：归属校验、订单状态与退还口径都在 getOwnPickupCode 内完成。
        // 二维码只是展示形式，不能因为新增接口而绕过原有可见条件。
        String pickupCode = getOwnPickupCode(userId, orderId);
        return PickupQrCodeRenderer.base64Png(pickupCode);
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

    private BusinessException notFound() {
        return new BusinessException(CommonErrorCode.NOT_FOUND, "取餐码无效或订单不可核销");
    }
}
