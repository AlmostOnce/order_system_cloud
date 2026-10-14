package com.hbue.ordering.auth.controller;

import com.hbue.ordering.auth.dto.request.LoginRequest;
import com.hbue.ordering.auth.dto.request.RegisterRequest;
import com.hbue.ordering.auth.dto.request.RefreshTokenRequest;
import com.hbue.ordering.auth.service.AuthService;
import com.hbue.ordering.auth.vo.LoginVO;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.user.api.vo.UserBasicVO;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * <p>负责接收登录、注册、刷新和注销 HTTP 请求，并将业务交给认证服务；本层
 * 不直接校验密码、不访问 Redis 或用户数据库。请求 DTO 的 Bean Validation
 * 在进入业务方法前执行，注销所需的 Access Token 由 Spring Security 注入。</p>
 *
 * @author order-system
 * @date 2026-09-20
 */
@RestController
@Tag(name = "认证接口")
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
     * @throws com.hbue.ordering.common.core.exception.BusinessException 账号密码错误或用户服务不可用时由统一异常处理转换为响应
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
     * @throws com.hbue.ordering.common.core.exception.BusinessException Refresh Token 无效、已被消费或刷新依赖不可用时由统一异常处理转换为响应
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
     * @throws com.hbue.ordering.common.core.exception.BusinessException Token 无效、Token 归属不匹配或撤销失败时由统一异常处理转换为响应
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
     * @throws com.hbue.ordering.common.core.exception.BusinessException 注册冲突或用户服务不可用时由统一异常处理转换为响应
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
