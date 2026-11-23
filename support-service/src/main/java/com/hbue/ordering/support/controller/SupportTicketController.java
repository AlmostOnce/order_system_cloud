package com.hbue.ordering.support.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.support.dto.AddSupportMessageRequest;
import com.hbue.ordering.support.dto.CreateSupportTicketRequest;
import com.hbue.ordering.support.dto.SupportCloseRequest;
import com.hbue.ordering.support.service.SupportTicketService;
import com.hbue.ordering.support.vo.SupportTicketDetailVO;
import com.hbue.ordering.support.vo.SupportTicketPageVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生端反馈工单接口。
 *
 * <p>只允许 CUSTOMER 角色访问；用户编号取自已验证 JWT 的 sub，任何查询与写入
 * 都以该编号作为归属条件。创建与补充说明要求请求头幂等键，重复提交安全返回
 * 已存在结果，不会重复建单或重复追加消息。</p>
 *
 * @author order-system
 * @date 2026-11-23
 */
@RestController
@Tag(name = "学生反馈接口")
@RequestMapping("/support/tickets")
@PreAuthorize("hasRole('CUSTOMER')")
public class SupportTicketController {

    /**
     * 学生反馈业务服务。
     */
    private final SupportTicketService ticketService;

    /**
     * 创建学生反馈接口控制器。
     *
     * @param ticketService 学生反馈业务服务
     */
    public SupportTicketController(SupportTicketService ticketService) {
        this.ticketService = ticketService;
    }

    /**
     * 提交反馈。
     *
     * <p>计划约定使用 {@code Idempotency-Key}，同时兼容历史调用可能发送的
     * {@code X-Request-Key}。两个头都声明为非必需，由
     * {@link #requireRequestKey(String, String)} 取非空值并交由业务层校验
     * 8-64 位格式，这样无需改动全局请求封装，也不会因缺少某一请求头直接返回 400。</p>
     *
     * @param jwt            已认证学生身份
     * @param authorization  原始 Bearer 凭据，用于订单归属校验
     * @param requestKey     兼容用的幂等请求头，可为空
     * @param idempotencyKey 计划约定的幂等请求头，可为空
     * @param request        提交内容
     * @return 新建或已存在的工单详情
     */
    @PostMapping
    public ApiResponse<SupportTicketDetailVO> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestHeader(name = "X-Request-Key", required = false) String requestKey,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody @Valid CreateSupportTicketRequest request) {
        Long customerId = Long.valueOf(jwt.getSubject());
        String key = requireRequestKey(requestKey, idempotencyKey);
        return ApiResponse.success(ticketService.create(customerId, authorization, request, key));
    }

    /**
     * 查询本人反馈列表。
     *
     * @param jwt    已认证学生身份
     * @param cursor 上一页最后一条工单标识
     * @param limit  每页条数，默认 20，最大 50
     * @return 游标分页结果
     */
    @GetMapping
    public ApiResponse<SupportTicketPageVO> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        Long customerId = Long.valueOf(jwt.getSubject());
        return ApiResponse.success(ticketService.page(customerId, cursor, limit));
    }

    /**
     * 查询本人反馈详情。
     *
     * @param jwt      已认证学生身份
     * @param ticketId 公开工单标识
     * @return 工单详情
     */
    @GetMapping("/{ticketId}")
    public ApiResponse<SupportTicketDetailVO> detail(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("ticketId") String ticketId) {
        Long customerId = Long.valueOf(jwt.getSubject());
        return ApiResponse.success(ticketService.detail(customerId, ticketId));
    }

    /**
     * 补充说明。
     *
     * <p>与创建一致，同时接受计划约定的 {@code Idempotency-Key} 与兼容用的
     * {@code X-Request-Key}；两者都为空时由业务层返回「缺少请求编号」，
     * 避免控制器层抛出的框架异常绕过统一响应体。</p>
     *
     * @param jwt            已认证学生身份
     * @param ticketId       公开工单标识
     * @param requestKey     兼容用的幂等请求头，可为空
     * @param idempotencyKey 计划约定的幂等请求头，可为空
     * @param request        补充内容
     * @return 补充后的工单详情
     */
    @PostMapping("/{ticketId}/messages")
    public ApiResponse<SupportTicketDetailVO> addMessage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("ticketId") String ticketId,
            @RequestHeader(name = "X-Request-Key", required = false) String requestKey,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody @Valid AddSupportMessageRequest request) {
        Long customerId = Long.valueOf(jwt.getSubject());
        String key = requireRequestKey(requestKey, idempotencyKey);
        return ApiResponse.success(ticketService.addMessage(customerId, ticketId, key, request));
    }

    /**
     * 关闭本人反馈。
     *
     * @param jwt      已认证学生身份
     * @param ticketId 公开工单标识
     * @param request  关闭原因，可空
     * @return 关闭后的工单详情
     */
    @PostMapping("/{ticketId}/close")
    public ApiResponse<SupportTicketDetailVO> close(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("ticketId") String ticketId,
            @RequestBody(required = false) SupportCloseRequest request) {
        Long customerId = Long.valueOf(jwt.getSubject());
        return ApiResponse.success(ticketService.close(customerId, ticketId, request));
    }

    /**
     * 从两种候选请求头中取幂等键。
     *
     * <p>优先使用计划约定的 {@code Idempotency-Key}，为空时回退到兼容头
     * {@code X-Request-Key}。这里不做格式判断，保持控制器只负责取值，
     * 格式错误由业务层统一转换为可展示的中文提示。</p>
     *
     * @param requestKey     兼容用的幂等请求头
     * @param idempotencyKey 计划约定的幂等请求头
     * @return 非空的幂等键；两者都为空时返回 null 交由业务层拒绝
     */
    private String requireRequestKey(String requestKey, String idempotencyKey) {
        // Idempotency-Key 是计划标准头，存在有效值时优先采用，避免两套头内容不一致时产生歧义。
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return idempotencyKey;
        }
        // 回退到 X-Request-Key，兼容仍按旧约定发送该头的调用方。
        if (requestKey != null && !requestKey.isBlank()) {
            return requestKey;
        }
        // 两个头都缺失时返回 null，由业务层抛出缺少请求编号的业务异常。
        return null;
    }
}
