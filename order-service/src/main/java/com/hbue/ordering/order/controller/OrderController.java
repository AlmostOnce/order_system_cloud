package com.hbue.ordering.order.controller;


import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.order.dto.request.OrderCreateRequest;
import com.hbue.ordering.order.dto.request.OrderStatusUpdateRequest;
import com.hbue.ordering.order.service.OrderSalesReportService;
import com.hbue.ordering.order.service.OrderService;
import com.hbue.ordering.order.vo.AdminSalesReportVO;
import com.hbue.ordering.order.vo.OrderDetailVO;
import com.hbue.ordering.order.vo.OrderPageVO;
import com.hbue.ordering.order.vo.OrderStatusVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 顾客和商家使用的订单 HTTP 接口。
 *
 * <p>本控制器负责接收请求、从已验证的 JWT 提取操作者身份并调用订单服务，
 * 不接受客户端自行指定的用户归属作为授权依据。角色权限由方法级安全注解限制，
 * 订单所有权、商家数据范围和状态迁移规则由 Service 再次校验。</p>
 *
 * @author order-system
 * @date 2026-10-14
 */
@RestController
@Tag(name = "订单接口")
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderSalesReportService orderSalesReportService;
    private final com.hbue.ordering.order.service.OrderWindowNameService windowNames;

    /**
     * 创建订单 HTTP 接口控制器。
     *
     * @param orderService            订单业务服务
     * @param orderSalesReportService 经营报表业务服务
     * @param windowNames             店铺名称补全服务
     */
    public OrderController(OrderService orderService,
                           OrderSalesReportService orderSalesReportService,
                           com.hbue.ordering.order.service.OrderWindowNameService windowNames) {
        this.orderService = orderService;
        this.orderSalesReportService = orderSalesReportService;
        this.windowNames = windowNames;
    }

    /**
     * 返回订单服务存活探测信息。
     *
     * @return 订单服务运行状态文本
     */
    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.success("order service is running!");
    }

    /**
     * 查询本人订单游标页。状态筛选交给Service及数据库，不再返回当前用户全部历史记录。
     *
     * @param jwt    已认证身份，不接受请求参数指定其他顾客
     * @param status 五组筛选编码，默认all
     * @param cursor 上一页返回的nextCursor，首屏不传
     * @param limit  每页条数，默认20，上限50
     * @return 当前页摘要、下一页游标与是否还有更多
     */
    @GetMapping("/list")
    public ApiResponse<OrderPageVO> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "status", defaultValue = "all") String status,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", defaultValue = "20") int limit
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        var page = orderService.pageByUserId(userId, status, cursor, limit);
        // 先分页再批量补名称：一页最多50个窗口，复用现有批量接口，禁止每笔订单单独远程请求。
        return ApiResponse.success(new OrderPageVO(
                windowNames.enrich(page.items()), page.nextCursor(), page.hasMore()));
    }

    /**
     * 根据订单 ID 查询当前用户的订单详情。
     *
     * @param jwt     当前登录用户 JWT
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
        return ApiResponse.success(windowNames.enrich(List.of(orderDetailVO)).getFirst());
    }


    /**
     * 创建订单。
     *
     * @param jwt            当前登录用户 JWT
     * @param idempotencyKey 客户端幂等键
     * @param request        创建订单请求
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
        return ApiResponse.success(windowNames.enrich(List.of(orderDetailVO)).getFirst());
    }

    /**
     * 顾客取消自己尚未支付的订单。
     *
     * @param jwt     当前登录顾客 JWT
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
     * @param jwt     当前登录操作者 JWT
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

    /**
     * 商家按档口读取订单队列。
     *
     * <p>只允许档口负责人与餐厅经理访问；Service 会校验档口是否在操作者数据范围内，
     * 越权与档口不存在统一按 404 处理。店铺名称按当前页批量补全，不逐单远程调用。</p>
     *
     * @param jwt      当前登录操作者 JWT
     * @param windowId 目标档口 ID
     * @param status   状态筛选：all、active、paid、preparing、waiting、completed、cancelled
     * @param cursor   上一页返回的 nextCursor，首屏不传
     * @param limit    每页条数，默认 20，上限 50
     * @return 当前页订单摘要、下一页游标与是否还有更多
     */
    @PreAuthorize("hasAnyRole('STALL_MANAGER','RESTAURANT_MANAGER')")
    @GetMapping("/admin/queue")
    public ApiResponse<OrderPageVO> windowQueue(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "windowId") Long windowId,
            @RequestParam(name = "status", defaultValue = "all") String status,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", defaultValue = "20") int limit
    ) {
        Long operatorId = Long.valueOf(jwt.getSubject());
        var page = orderService.pageByWindow(operatorId, windowId, status, cursor, limit);
        // 先分页再批量补名称：一页最多 50 个窗口，复用现有批量接口。
        return ApiResponse.success(new OrderPageVO(
                windowNames.enrich(page.items()), page.nextCursor(), page.hasMore()));
    }

    /**
     * 管理端经营报表。
     *
     * <p>档口负责人、餐厅经理与总经理可访问；指定档口时校验该档口在操作者范围内，
     * 未指定时按操作者范围汇总。所有金额、订单数、份数与客单价均由服务端聚合后返回，
     * 前端只做展示与时间筛选。</p>
     *
     * @param jwt      当前登录操作者 JWT
     * @param windowId 可选档口 ID；为空表示按操作者范围汇总
     * @param from     区间起点，ISO 日期时间
     * @param to       区间终点（不含），ISO 日期时间
     * @return 经营报表
     */
    @PreAuthorize("hasAnyRole('STALL_MANAGER','RESTAURANT_MANAGER','GENERAL_MANAGER')")
    @GetMapping("/admin/sales-report")
    public ApiResponse<AdminSalesReportVO> salesReport(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "windowId", required = false) Long windowId,
            @RequestParam(name = "from")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(name = "to")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        Long operatorId = Long.valueOf(jwt.getSubject());
        return ApiResponse.success(orderSalesReportService.summarize(operatorId, windowId, from, to));
    }
}
