package com.hbue.ordering.auth.controller;

import com.hbue.ordering.auth.dto.request.LoginRequest;
import com.hbue.ordering.auth.dto.request.RegisterRequest;
import com.hbue.ordering.auth.dto.request.RefreshTokenRequest;
import com.hbue.ordering.auth.service.AuthService;
import com.hbue.ordering.auth.vo.LoginVO;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.user.api.vo.UserBasicVO;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器。
 *
 * <p>负责处理用户登录等认证请求。</p>
 *
 * @author order-system
 * @date 2026-09-20
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    /**
     * 创建认证控制器。
     *
     * @param authService 认证业务服务
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 用户登录。
     *
     * @param request 登录请求
     * @return JWT 登录 Token
     */
    @PostMapping("/login")
    public ApiResponse<LoginVO> login(
            @Valid @RequestBody LoginRequest request
    ) {
        // 由 Service 完成密码校验和 JWT 签发。
        LoginVO loginVO = authService.login(request);

        // 返回登录 Token。
        return ApiResponse.success(loginVO);
    }

    /**
     * 使用 Refresh Token 刷新登录 Token。
     *
     * @param request Refresh Token 刷新请求
     * @return 新的登录 Token
     */
    @PostMapping("/refresh")
    public ApiResponse<LoginVO> refresh(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        // 由 Service 校验 Refresh Token 并完成 Token 轮换。
        LoginVO loginVO = authService.refreshAccessToken(request);

        // 返回新的登录 Token。
        return ApiResponse.success(loginVO);
    }

    /**
     * 用户注销。
     *
     * @param request 注销请求，包含需要撤销的 Refresh Token
     * @param accessToken 当前已认证的访问令牌
     * @return 注销结果
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @Valid @RequestBody RefreshTokenRequest request,
            @AuthenticationPrincipal Jwt accessToken
    ) {
        // 由 Service 校验 Refresh Token 归属并撤销当前会话的两个令牌。
        authService.logout(request, accessToken);

        // 返回注销成功响应。
        return ApiResponse.success(null);
    }


    /**
     * 用户注册。
     *
     * @param request 注册请求
     * @return 注册成功的用户基础信息
     */
    @PostMapping("/register")
    public ApiResponse<UserBasicVO> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        // 由 Service 调用用户服务完成注册。
        UserBasicVO userBasicVO =
                authService.register(request);

        // 返回注册成功的用户信息。
        return ApiResponse.success(userBasicVO);
    }
}
