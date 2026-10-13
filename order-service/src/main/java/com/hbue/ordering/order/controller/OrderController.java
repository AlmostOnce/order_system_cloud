package com.hbue.ordering.order.controller;


import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.order.dto.request.OrderCreateRequest;
import com.hbue.ordering.order.dto.request.OrderStatusUpdateRequest;
import com.hbue.ordering.order.model.OrderDO;
import com.hbue.ordering.order.service.OrderService;
import com.hbue.ordering.order.vo.OrderDetailVO;
import com.hbue.ordering.order.vo.OrderStatusVO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/ping")
    public ApiResponse<String> ping () {
        return ApiResponse.success("order service is running!");
    }

    /**
     * 查询当前用户的订单列表。
     *
     * @param jwt 当前登录用户 JWT
     * @return 当前用户的订单列表
     */
    @GetMapping("/list")
    public ApiResponse<List<OrderDetailVO>> list(
            @AuthenticationPrincipal Jwt jwt
    ) {
        // 从 JWT 的 sub 声明中获取当前用户 ID。
        Long userId = Long.valueOf(jwt.getSubject());

        // 只查询当前用户自己的订单。
        List<OrderDO> orders =
                orderService.listByUserId(userId);

        // 创建订单视图对象列表。
        List<OrderDetailVO> orderVOList =
                new ArrayList<>();

        // 使用普通循环完成对象转换。
        for (OrderDO orderDO : orders) {
            orderVOList.add(OrderDetailVO.from(orderDO));
        }

        // 返回当前用户的订单列表。
        return ApiResponse.success(orderVOList);
    }

    /**
     * 根据订单 ID 查询当前用户的订单详情。
     *
     * @param jwt 当前登录用户 JWT
     * @param orderId 订单 ID
     * @return 订单详情
     */
    @GetMapping("/{orderId}")
    public ApiResponse<OrderDetailVO> getOrderById(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("orderId") Long orderId
    ) {
        // 从 JWT 的 sub 声明中获取当前用户 ID。
        Long userId = Long.valueOf(jwt.getSubject());

        // Service 会校验订单是否属于当前用户。
        OrderDetailVO orderDetailVO =
                orderService.getOrderDetailById(
                        userId,
                        orderId
                );

        // 返回订单详情。
        return ApiResponse.success(orderDetailVO);
    }


    /**
     * 创建订单。
     *
     * @param jwt 当前登录用户 JWT
     * @param idempotencyKey 客户端幂等键
     * @param request 创建订单请求
     * @return 新创建的订单详情
     */
    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping
    public ApiResponse<OrderDetailVO> createOrder(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "Idempotency-Key", required = false)
            String idempotencyKey,
            @Valid @RequestBody OrderCreateRequest request
    ) {
        // 从 JWT 中获取当前登录用户 ID。
        Long userId = Long.valueOf(jwt.getSubject());

        // 创建订单。
        OrderDetailVO orderDetailVO =
                orderService.createOrder(
                        userId,
                        idempotencyKey,
                        request
                );

        // 返回订单详情。
        return ApiResponse.success(orderDetailVO);
    }

    /**
     * 顾客取消自己尚未支付的订单。
     *
     * @param jwt 当前登录顾客 JWT
     * @param orderId 订单 ID
     * @return 变更后的订单状态
     */
    @PreAuthorize("hasRole('CUSTOMER')")
    @PatchMapping("/{orderId}/cancel")
    public ApiResponse<OrderStatusVO> cancelOrder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("orderId") Long orderId
    ) {
        // 从 JWT 中获取当前登录顾客 ID。
        Long userId = Long.valueOf(jwt.getSubject());

        // Service 校验订单归属、订单状态并执行原子状态更新。
        OrderStatusVO statusVO = orderService.cancelOrder(userId, orderId);

        // 返回状态更新结果。
        return ApiResponse.success(statusVO);
    }

    /**
     * 商家按合法履约顺序推进订单状态。
     *
     * <p>只允许档口负责人与餐厅经理调用；技术角色不参与订单履约。
     * Service 会校验订单是否落在操作者的数据范围内，越权按不存在处理。</p>
     *
     * @param jwt 当前登录操作者 JWT
     * @param orderId 订单 ID
     * @param request 目标订单状态
     * @return 变更后的订单状态
     */
    @PreAuthorize("hasAnyRole('STALL_MANAGER','RESTAURANT_MANAGER')")
    @PatchMapping("/{orderId}/status")
    public ApiResponse<OrderStatusVO> advanceOrderStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("orderId") Long orderId,
            @Valid @RequestBody OrderStatusUpdateRequest request
    ) {
        // 从 JWT 的 sub 声明中获取操作者用户 ID，供范围校验与审计使用。
        Long operatorId = Long.valueOf(jwt.getSubject());

        // Service 校验数据范围与合法迁移并执行原子状态更新。
        OrderStatusVO statusVO = orderService.advanceOrderStatus(
                operatorId,
                orderId,
                request.getStatus()
        );

        // 返回状态更新结果。
        return ApiResponse.success(statusVO);
    }
}
