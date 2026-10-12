package com.hbue.ordering.order.controller;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.order.model.OrderDO;
import com.hbue.ordering.order.security.InternalTokenVerifier;
import com.hbue.ordering.order.service.OrderService;
import com.hbue.ordering.order.vo.OrderInternalVO;
import com.hbue.ordering.order.vo.OrderStatusVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单服务内部接口，仅供支付服务经服务发现调用。
 *
 * <p>鉴权说明：与代表用户的 /internal/products/quote（透传用户 JWT）不同，
 * 支付回调驱动的订单推进没有用户上下文，属于"系统事件"调用，
 * 因此使用共享密钥（X-Internal-Token）认证。两种模式并存是刻意设计。</p>
 *
 * @author order-system
 * @date 2026-10-12
 */
@RestController
@RequestMapping("/internal/orders")
public class OrderInternalController {

    /** 内部令牌请求头名称。 */
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    /** 订单业务服务。 */
    private final OrderService orderService;

    /** 内部令牌校验器。 */
    private final InternalTokenVerifier internalTokenVerifier;

    /**
     * 创建订单服务内部接口。
     *
     * @param orderService 订单业务服务
     * @param internalTokenVerifier 内部令牌校验器
     */
    public OrderInternalController(
            OrderService orderService,
            InternalTokenVerifier internalTokenVerifier
    ) {
        this.orderService = orderService;
        this.internalTokenVerifier = internalTokenVerifier;
    }

    /**
     * 查询订单内部视图，供创建支付单时校验归属与状态。
     *
     * @param token 内部调用令牌
     * @param orderId 订单 ID
     * @return 内部订单视图
     */
    @GetMapping("/{orderId}")
    public ApiResponse<OrderInternalVO> getOrder(
            @RequestHeader(value = INTERNAL_TOKEN_HEADER, required = false)
            String token,
            @PathVariable("orderId") Long orderId
    ) {
        verifyInternalToken(token);
        OrderDO orderDO = orderService.getById(orderId);
        if (orderDO == null) {
            throw new BusinessException(
                    CommonErrorCode.NOT_FOUND,
                    "订单不存在"
            );
        }
        return ApiResponse.success(OrderInternalVO.from(orderDO));
    }

    /**
     * 支付成功后推进订单状态：待支付到已支付。
     *
     * @param token 内部调用令牌
     * @param orderId 订单 ID
     * @return 订单状态变更结果
     */
    @PatchMapping("/{orderId}/paid")
    public ApiResponse<OrderStatusVO> markOrderPaid(
            @RequestHeader(value = INTERNAL_TOKEN_HEADER, required = false)
            String token,
            @PathVariable("orderId") Long orderId
    ) {
        verifyInternalToken(token);
        return ApiResponse.success(orderService.markOrderPaid(orderId));
    }

    /**
     * 校验内部调用令牌，无效时返回统一 401。
     *
     * @param token 请求头中的内部令牌
     */
    private void verifyInternalToken(String token) {
        if (!internalTokenVerifier.verify(token)) {
            throw new BusinessException(
                    CommonErrorCode.UNAUTHORIZED,
                    "内部调用令牌无效"
            );
        }
    }
}
