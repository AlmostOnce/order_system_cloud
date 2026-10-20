package com.hbue.ordering.cart.service.impl;

import com.hbue.ordering.cart.dto.request.CartCleanupRequest;
import com.hbue.ordering.cart.dto.request.CartSubmittedItemRequest;
import com.hbue.ordering.cart.repository.CartRedisRepository;
import com.hbue.ordering.cart.service.CartCleanupService;
import com.hbue.ordering.cart.vo.CartCleanupVO;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.order.api.client.OrderServiceClient;
import com.hbue.ordering.order.api.client.fallback.OrderServiceClientFallbackFactory;
import com.hbue.ordering.order.api.vo.OrderCartSnapshotVO;
import com.hbue.ordering.order.api.vo.OrderPurchasedItemVO;
import feign.FeignException;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 下单成功后的安全清理实现：先核对订单事实，再按原始版本原子删除购物车条目。
 *
 * <p>不创建订单、不修改订单状态，也不依赖当前商品价格。清理故障只影响购物车，
 * 不能把已经提交的订单回滚；调用者可使用同一个订单 ID 和原始快照重试。</p>
 */
@Service
public class CartCleanupServiceImpl implements CartCleanupService {
    /** 用当前顾客凭据读取订单，服务端继续检查订单归属。 */
    private final OrderServiceClient orders;
    /** 只负责本用户 Hash 内的版本比较与原子删除。 */
    private final CartRedisRepository repository;

    /**
     * 注入订单读取和购物车存储边界，不依赖其它服务的实现类。
     * @param orders 订单读取客户端
     * @param repository 购物车 Redis 访问层
     */
    public CartCleanupServiceImpl(OrderServiceClient orders, CartRedisRepository repository) {
        this.orders = orders;
        this.repository = repository;
    }

    /**
     * 按提交快照清理，当前状态已变化的条目交由 Redis 脚本保留。
     * @param userId 已认证顾客 ID
     * @param orderId 已成功创建的订单 ID
     * @param request 下单前保存、重试时保持不变的快照
     * @return 实际删除与跳过的条目数
     */
    @Override
    public CartCleanupVO cleanup(Long userId, Long orderId, CartCleanupRequest request) {
        validateRequest(userId, orderId, request);
        OrderCartSnapshotVO order = queryOrder(orderId);
        // 除了远程归属校验，再检查响应身份，防止误路由或上游契约错误导致跨用户操作。
        if (order.userId() == null || order.orderId() == null || order.windowId() == null
                || order.items() == null || order.items().isEmpty()) {
            throw unavailable();
        }
        if (!userId.equals(order.userId()) || !orderId.equals(order.orderId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "订单不存在");
        }
        if (!request.windowId().equals(order.windowId())) {
            throw mismatch();
        }

        Map<Long, Integer> purchased = new HashMap<>();
        for (OrderPurchasedItemVO item : order.items()) {
            // 损坏的明细不能被当成合法购买证明；先验证整份订单，之后才允许访问 Redis。
            if (item == null || item.productId() == null || item.productId() < 1
                    || item.quantity() == null || item.quantity() < 1
                    || purchased.putIfAbsent(item.productId(), item.quantity()) != null) {
                throw unavailable();
            }
        }
        if (purchased.size() != request.items().size()) {
            throw mismatch();
        }
        for (CartSubmittedItemRequest item : request.items()) {
            if (!item.quantity().equals(purchased.get(item.productId()))) {
                throw mismatch();
            }
        }

        // Lua 同时完成读取、版本/数量/勾选比较与删除，消除 Java 先查后删的竞态窗口。
        long removed = repository.removeSubmitted(userId, request.windowId(), request.items());
        return new CartCleanupVO(orderId, removed, request.items().size() - removed);
    }

    /**
     * 在远程调用之前检查身份、容量、版本与重复商品；Service 独立调用也不能绕过校验。
     * @param userId 当前顾客 ID
     * @param orderId 已创建订单 ID
     * @param request 原始提交快照
     */
    private void validateRequest(Long userId, Long orderId, CartCleanupRequest request) {
        if (userId == null || userId < 1) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "登录用户身份无效");
        }
        if (orderId == null || orderId < 1 || request == null || request.windowId() == null
                || request.windowId() < 1 || request.items() == null || request.items().isEmpty()
                || request.items().size() > 100) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "订单和提交快照不能为空或超出限制");
        }
        Set<Long> ids = new HashSet<>();
        for (CartSubmittedItemRequest item : request.items()) {
            if (item == null || item.productId() == null || item.productId() < 1
                    || item.quantity() == null || item.quantity() < 1
                    || item.revision() == null || item.revision().isBlank() || item.revision().length() > 128
                    || !ids.add(item.productId())) {
                throw new BusinessException(CommonErrorCode.BAD_REQUEST, "商品、数量、版本无效或商品重复");
            }
        }
    }

    /**
     * 读取已有订单并保留 401/403/404，网络错误与降级统一返回 503。
     * @param orderId 已有订单 ID
     * @return 当前用户订单的购买事实
     */
    private OrderCartSnapshotVO queryOrder(Long orderId) {
        ApiResponse<OrderCartSnapshotVO> response;
        try {
            response = orders.getOrder(orderId);
        } catch (FeignException exception) {
            // 即使本机关闭 Sentinel Feign 适配，也使用相同的错误转换，不暴露原始 HTTP 内容。
            response = new OrderServiceClientFallbackFactory().create(exception).getOrder(orderId);
        }
        if (response == null) {
            throw unavailable();
        }
        if (!CommonErrorCode.SUCCESS.code().equals(response.getCode())) {
            for (CommonErrorCode code : new CommonErrorCode[]{CommonErrorCode.UNAUTHORIZED,
                    CommonErrorCode.FORBIDDEN, CommonErrorCode.NOT_FOUND}) {
                if (code.code().equals(response.getCode())) {
                    throw new BusinessException(code);
                }
            }
            throw unavailable();
        }
        if (response.getData() == null) {
            throw unavailable();
        }
        return response.getData();
    }

    /**
     * 创建快照不一致错误，提醒调用方不要用新查询的购物车替换原始下单快照。
     * @return HTTP 409 对应业务异常
     */
    private BusinessException mismatch() {
        return new BusinessException(CommonErrorCode.CONFLICT, "清理快照与订单购买明细不一致");
    }

    /**
     * 创建依赖不可用错误，明确订单不需要重建，只需稍后重试清理。
     * @return HTTP 503 对应业务异常
     */
    private BusinessException unavailable() {
        return new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE,
                "暂时无法核实订单，请保留原始快照稍后重试清理，不要重复下单");
    }
}
