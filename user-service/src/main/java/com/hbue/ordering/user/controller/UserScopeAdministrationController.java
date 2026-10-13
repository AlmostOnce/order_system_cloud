package com.hbue.ordering.user.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.user.dto.request.UserScopeSaveRequest;
import com.hbue.ordering.user.service.UserScopeAdministrationService;
import com.hbue.ordering.user.vo.UserScopeAdminVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户数据范围授予接口。
 *
 * <p>数据范围的授予权归业务角色：{@code RESTAURANT_MANAGER}（限本餐厅）与
 * {@code GENERAL_MANAGER}（全平台）。技术角色 {@code SYSTEM_ADMIN} 不具备该能力。
 * 每个接口会把当前操作者传给 Service，由 Service 完成"授予者不得越出自身范围"
 * 与"不得操作本人"两项校验（越权统一返回 403）。</p>
 *
 * @author order-system
 * @date 2026-10-13
 */
@Validated
@RestController
@RequestMapping("/users")
@PreAuthorize("hasAnyRole('RESTAURANT_MANAGER','GENERAL_MANAGER')")
public class UserScopeAdministrationController {

    /** 数据范围授予业务服务。 */
    private final UserScopeAdministrationService userScopeAdministrationService;

    /**
     * 创建用户数据范围授予接口。
     *
     * @param userScopeAdministrationService 数据范围授予业务服务
     */
    public UserScopeAdministrationController(
            UserScopeAdministrationService userScopeAdministrationService
    ) {
        this.userScopeAdministrationService = userScopeAdministrationService;
    }

    /**
     * 查询指定用户持有的数据范围。
     *
     * @param jwt 当前登录操作者 JWT
     * @param userId 目标用户 ID
     * @return 目标用户的数据范围列表
     */
    @GetMapping("/{userId}/scopes")
    public ApiResponse<List<UserScopeAdminVO>> listScopes(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("userId") @Positive Long userId
    ) {
        return ApiResponse.success(
                userScopeAdministrationService.listScopes(
                        operatorId(jwt),
                        userId
                )
        );
    }

    /**
     * 覆盖式设置指定用户的数据范围。
     *
     * @param jwt 当前登录操作者 JWT
     * @param userId 目标用户 ID
     * @param request 新的范围列表
     * @return 写入后的数据范围列表
     */
    @PutMapping("/{userId}/scopes")
    public ApiResponse<List<UserScopeAdminVO>> replaceScopes(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("userId") @Positive Long userId,
            @Valid @RequestBody UserScopeSaveRequest request
    ) {
        return ApiResponse.success(
                userScopeAdministrationService.replaceScopes(
                        operatorId(jwt),
                        userId,
                        request
                )
        );
    }

    /**
     * 撤销指定用户的一条数据范围。
     *
     * @param jwt 当前登录操作者 JWT
     * @param userId 目标用户 ID
     * @param scopeId 范围记录 ID
     * @return 空成功响应
     */
    @DeleteMapping("/{userId}/scopes/{scopeId}")
    public ApiResponse<Void> revokeScope(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("userId") @Positive Long userId,
            @PathVariable("scopeId") @Positive Long scopeId
    ) {
        userScopeAdministrationService.revokeScope(
                operatorId(jwt),
                userId,
                scopeId
        );
        return ApiResponse.success(null);
    }

    /**
     * 从 JWT 中取出当前操作者 ID。
     *
     * @param jwt 当前登录操作者 JWT
     * @return 操作者用户 ID
     */
    private Long operatorId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
