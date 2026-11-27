package com.hbue.ordering.support.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.support.dto.SupportCloseRequest;
import com.hbue.ordering.support.dto.SupportInternalNoteRequest;
import com.hbue.ordering.support.dto.SupportReplyRequest;
import com.hbue.ordering.support.service.SupportActor;
import com.hbue.ordering.support.service.SupportManagementService;
import com.hbue.ordering.support.support.SupportRole;
import com.hbue.ordering.support.vo.SupportManagementPageVO;
import com.hbue.ordering.support.vo.SupportManagementTicketVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.Set;

/**
 * 管理端反馈工单接口。
 *
 * <p>接待经理、总经理和系统管理员使用。角色注解只限制接口入口，Service 还会
 * 在每次操作前读取数据范围并校验工单类型与餐厅归属；前端不展示越权入口不代表
 * 后端可以省略校验。</p>
 *
 * @author order-system
 * @date 2026-11-23
 */
@RestController
@Tag(name = "管理端反馈接口")
@RequestMapping("/support/management/tickets")
@PreAuthorize(SupportRole.MANAGEMENT_ACCESS)
public class SupportManagementController {

    /**
     * 管理端反馈业务服务。
     */
    private final SupportManagementService managementService;

    /**
     * 创建管理端反馈接口控制器。
     *
     * @param managementService 管理端反馈业务服务
     */
    public SupportManagementController(SupportManagementService managementService) {
        this.managementService = managementService;
    }

    /**
     * 查询管理端工单队列。
     *
     * @param jwt      已认证员工身份
     * @param auth     当前认证信息，用于提取角色
     * @param status   状态筛选，可空
     * @param type     类型筛选，可空
     * @param windowId 窗口筛选，可空
     * @param cursor   游标，可空
     * @param limit    每页条数
     * @return 管理端分页结果
     */
    @GetMapping
    public ApiResponse<SupportManagementPageVO> list(
            @AuthenticationPrincipal Jwt jwt,
            Authentication auth,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "windowId", required = false) Long windowId,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return ApiResponse.success(managementService.page(
                actor(jwt, auth), status, type, windowId, cursor, limit));
    }

    /**
     * 查询管理端工单详情。
     *
     * @param jwt      已认证员工身份
     * @param auth     当前认证信息
     * @param ticketId 公开工单标识
     * @return 含内部备注的工单详情
     */
    @GetMapping("/{ticketId}")
    public ApiResponse<SupportManagementTicketVO> detail(
            @AuthenticationPrincipal Jwt jwt,
            Authentication auth,
            @PathVariable("ticketId") String ticketId) {
        return ApiResponse.success(managementService.detail(actor(jwt, auth), ticketId));
    }

    /**
     * 受理工单。
     *
     * @param jwt      已认证员工身份
     * @param auth     当前认证信息
     * @param ticketId 公开工单标识
     * @return 受理后的工单详情
     */
    @PostMapping("/{ticketId}/accept")
    public ApiResponse<SupportManagementTicketVO> accept(
            @AuthenticationPrincipal Jwt jwt,
            Authentication auth,
            @PathVariable("ticketId") String ticketId) {
        return ApiResponse.success(managementService.accept(actor(jwt, auth), ticketId));
    }

    /**
     * 公开回复学生。
     *
     * @param jwt      已认证员工身份
     * @param auth     当前认证信息
     * @param ticketId 公开工单标识
     * @param request  回复内容
     * @return 回复后的工单详情
     */
    @PostMapping("/{ticketId}/reply")
    public ApiResponse<SupportManagementTicketVO> reply(
            @AuthenticationPrincipal Jwt jwt,
            Authentication auth,
            @PathVariable("ticketId") String ticketId,
            @RequestBody @Valid SupportReplyRequest request) {
        return ApiResponse.success(managementService.reply(actor(jwt, auth), ticketId, request));
    }

    /**
     * 添加内部备注。
     *
     * @param jwt      已认证员工身份
     * @param auth     当前认证信息
     * @param ticketId 公开工单标识
     * @param request  备注内容
     * @return 添加后的工单详情
     */
    @PostMapping("/{ticketId}/notes")
    public ApiResponse<SupportManagementTicketVO> addInternalNote(
            @AuthenticationPrincipal Jwt jwt,
            Authentication auth,
            @PathVariable("ticketId") String ticketId,
            @RequestBody @Valid SupportInternalNoteRequest request) {
        return ApiResponse.success(managementService.addInternalNote(actor(jwt, auth), ticketId, request));
    }

    /**
     * 关闭工单。
     *
     * @param jwt      已认证员工身份
     * @param auth     当前认证信息
     * @param ticketId 公开工单标识
     * @param request  关闭原因，可空
     * @return 关闭后的工单详情
     */
    @PostMapping("/{ticketId}/close")
    public ApiResponse<SupportManagementTicketVO> close(
            @AuthenticationPrincipal Jwt jwt,
            Authentication auth,
            @PathVariable("ticketId") String ticketId,
            @RequestBody(required = false) SupportCloseRequest request) {
        return ApiResponse.success(managementService.close(actor(jwt, auth), ticketId, request));
    }

    /**
     * 从 JWT 与认证信息构造操作者快照。
     *
     * @param jwt  已认证员工身份
     * @param auth 当前认证信息
     * @return 操作者快照
     */
    private SupportActor actor(Jwt jwt, Authentication auth) {
        Set<String> roles = new HashSet<>();
        for (GrantedAuthority authority : auth.getAuthorities()) {
            String value = authority.getAuthority();
            roles.add(value.startsWith("ROLE_") ? value.substring("ROLE_".length()) : value);
        }
        return new SupportActor(Long.valueOf(jwt.getSubject()), Set.copyOf(roles), null);
    }
}
