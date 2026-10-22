package com.hbue.ordering.user.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.user.api.vo.UserBasicVO;
import com.hbue.ordering.user.dto.request.UserRoleUpdateRequest;
import com.hbue.ordering.user.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户角色分配接口。
 *
 * <p>角色是技术身份，仅 {@code SYSTEM_ADMIN} 可分配；该接口只写
 * {@code users.role_code}，不涉及数据范围。技术角色还受硬约束：禁止修改本人
 * 角色编码（对外表现为 403），避免自我提权绕过职责分离。</p>
 *
 * @author order-system
 * @date 2026-10-13
 */
@Validated
@RestController
@Tag(name = "角色权限管理")
@RequestMapping("/users")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class UserRoleAdministrationController {

    /** 用户业务服务。 */
    private final UserService userService;

    /**
     * 创建用户角色分配接口。
     *
     * @param userService 用户业务服务
     */
    public UserRoleAdministrationController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 修改指定用户的角色编码。
     *
     * @param jwt 当前登录操作者 JWT
     * @param userId 目标用户 ID
     * @param request 目标角色编码
     * @return 变更后的用户基础信息
     * @throws com.hbue.ordering.common.core.exception.BusinessException 目标角色或用户无效、或操作者试图修改自身角色时由统一异常处理转换为响应
     */
    @PutMapping("/{userUuid}/role")
    public ApiResponse<com.hbue.ordering.user.api.vo.UserPublicVO> assignRole(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("userUuid") String userUuid,
            @Valid @RequestBody UserRoleUpdateRequest request
    ) {
        Long userId = userService.resolvePublicUserId(userUuid);
        // 操作者 ID 取自已验签 JWT；目标角色是否允许及是否属于本人由 Service 再次校验。
        return ApiResponse.success(
                com.hbue.ordering.user.api.vo.UserPublicVO.from(userService.assignRole(
                        operatorId(jwt),
                        userId,
                        request.getRoleCode()
                ))
        );
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
