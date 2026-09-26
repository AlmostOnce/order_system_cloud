package com.hbue.ordering.auth.service;

import com.hbue.ordering.auth.dto.request.LoginRequest;
import com.hbue.ordering.auth.dto.request.RegisterRequest;
import com.hbue.ordering.auth.dto.request.RefreshTokenRequest;
import com.hbue.ordering.user.api.vo.UserBasicVO;
import com.hbue.ordering.auth.vo.LoginVO;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 认证业务服务。
 *
 * <p>负责处理用户登录和用户注册相关业务。</p>
 *
 * @author order-system
 * @date 2026-09-21
 */
public interface AuthService {

    /**
     * 用户登录。
     *
     * @param request 登录请求
     * @return 登录 Token
     */
    LoginVO login(LoginRequest request);

    /**
     * 使用 Refresh Token 刷新登录 Token。
     *
     * @param request Refresh Token 刷新请求
     * @return 新的登录 Token
     */
    LoginVO refreshAccessToken(RefreshTokenRequest request);

    /**
     * 用户注销。
     *
     * @param request 注销请求，包含需要撤销的 Refresh Token
     * @param accessToken 当前已通过认证的 Access Token
     */
    void logout(RefreshTokenRequest request, Jwt accessToken);

    /**
     * 用户注册。
     *
     * @param request 注册请求
     * @return 注册成功的用户基础信息
     */
    UserBasicVO register(RegisterRequest request);
}
