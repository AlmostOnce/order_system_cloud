package com.hbue.ordering.auth.service;

import com.hbue.ordering.auth.dto.request.LoginRequest;
import com.hbue.ordering.auth.dto.request.RegisterRequest;
import com.hbue.ordering.auth.dto.request.RefreshTokenRequest;
import com.hbue.ordering.user.api.vo.UserBasicVO;
import com.hbue.ordering.auth.vo.LoginVO;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 认证业务服务契约。
 *
 * <p>负责协调认证流程：通过 user-service 校验或创建用户，在登录后签发
 * Access Token 并保存 Refresh Token；登录失败状态由 Redis 共享维护。用户数据
 * 仍由 user-service 持有，认证服务不直接访问用户数据库。</p>
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
     * @throws com.hbue.ordering.common.core.exception.BusinessException
     *         凭据无效、账号临时锁定或依赖服务不可用时抛出
     */
    LoginVO login(LoginRequest request);

    /**
     * 使用 Refresh Token 刷新登录 Token。
     *
     * @param request Refresh Token 刷新请求
     * @return 新的登录 Token
     * @throws com.hbue.ordering.common.core.exception.BusinessException
     *         Refresh Token 无效、并发消费失败或锁服务不可用时抛出
     */
    LoginVO refreshAccessToken(RefreshTokenRequest request);

    /**
     * 用户注销。
     *
     * @param request 注销请求，包含需要撤销的 Refresh Token
     * @param accessToken 当前已通过认证的 Access Token
     * @throws com.hbue.ordering.common.core.exception.BusinessException
     *         当前身份无效、Refresh Token 归属不匹配或撤销操作失败时抛出
     */
    void logout(RefreshTokenRequest request, Jwt accessToken);

    /**
     * 用户注册。
     *
     * @param request 注册请求
     * @return 注册成功的用户基础信息
     * @throws com.hbue.ordering.common.core.exception.BusinessException
     *         用户服务不可用或注册参数与既有账号冲突时抛出
     */
    UserBasicVO register(RegisterRequest request);
}
