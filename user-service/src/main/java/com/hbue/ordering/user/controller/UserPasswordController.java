package com.hbue.ordering.user.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.user.dto.request.ChangeOwnPasswordRequest;
import com.hbue.ordering.user.service.PasswordChangeService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生主动修改登录密码接口。
 *
 * <p>只允许 CUSTOMER 角色访问，用户编号取自 JWT 的 sub，不接受请求体指定账号。
 * 当前密码校验、失败次数限流、凭据版本递增与会话失效均由业务层完成；
 * 成功后客户端必须重新登录，旧访问令牌和刷新令牌不能继续使用。</p>
 *
 * @author order-system
 * @date 2026-11-23
 */
@RestController
@RequestMapping("/users/me/password")
public class UserPasswordController {

    /**
     * 修改密码业务服务。
     */
    private final PasswordChangeService passwordChangeService;

    /**
     * 创建修改密码接口。
     *
     * @param passwordChangeService 修改密码业务服务
     */
    public UserPasswordController(PasswordChangeService passwordChangeService) {
        this.passwordChangeService = passwordChangeService;
    }

    /**
     * 校验当前密码后修改本人登录密码。
     *
     * @param jwt     当前登录凭据，sub 为内部用户编号
     * @param request 当前密码与新密码
     * @return 成功响应，无业务数据
     */
    @PutMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<Void> changeOwnPassword(@AuthenticationPrincipal Jwt jwt,
                                               @Valid @RequestBody ChangeOwnPasswordRequest request) {
        passwordChangeService.changeOwnPassword(Long.valueOf(jwt.getSubject()), request);
        return ApiResponse.<Void>success(null);
    }
}
