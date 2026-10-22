package com.hbue.ordering.user.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.user.dto.request.UserStatusUpdateRequest;
import com.hbue.ordering.user.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户账号状态管理接口。
 *
 * <p>账号生命周期（启用/禁用）属于技术角色的职责，仅 {@code SYSTEM_ADMIN} 可操作；
 * 该接口只写 {@code users.status}，不触碰任何业务数据。受硬约束③保护：禁止禁用
 * 最后一个处于启用状态的 {@code SYSTEM_ADMIN}（对外表现为 409），避免系统进入
 * "无人可管理"的死局。</p>
 *
 * @author order-system
 * @date 2026-10-13
 */
@Validated
@RestController
@Tag(name = "账号状态管理")
@RequestMapping("/users")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class UserStatusAdministrationController {

    /** 用户业务服务。 */
    private final UserService userService;

    /**
     * 创建用户账号状态管理接口。
     *
     * @param userService 用户业务服务
     */
    public UserStatusAdministrationController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 启用或禁用指定账号。
     *
     * @param jwt 当前登录操作者 JWT
     * @param userId 目标用户 ID
     * @param request 目标状态：1 启用，0 禁用
     * @return 无数据的成功响应
     * @throws com.hbue.ordering.common.core.exception.BusinessException 状态不合法、目标用户不存在或禁用最后一个启用的系统管理员时由统一异常处理转换为响应
     */
    @PatchMapping("/{userUuid}/status")
    public ApiResponse<Void> updateUserStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("userUuid") String userUuid,
            @Valid @RequestBody UserStatusUpdateRequest request
    ) {
        Long userId = userService.resolvePublicUserId(userUuid);
        // 操作者身份来自已验签 JWT，Service 负责最后一个系统管理员保护和审计。
        userService.updateUserStatus(
                operatorId(jwt),
                userId,
                request.getStatus()
        );
        return ApiResponse.success(null);
    }

    /**
     * 从已通过资源服务器校验的 JWT 中取出当前操作者 ID。
     *
     * @param jwt 当前登录操作者 JWT
     * @return 操作者用户 ID
     */
    private Long operatorId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
