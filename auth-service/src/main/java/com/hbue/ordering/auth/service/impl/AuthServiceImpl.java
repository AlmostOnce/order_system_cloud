package com.hbue.ordering.auth.service.impl;

import com.hbue.ordering.auth.dto.request.LoginRequest;
import com.hbue.ordering.auth.dto.request.RegisterRequest;
import com.hbue.ordering.auth.dto.request.RefreshTokenRequest;
import com.hbue.ordering.auth.model.RefreshTokenInfo;
import com.hbue.ordering.auth.service.AuthService;
import com.hbue.ordering.auth.service.AccessTokenBlacklistService;
import com.hbue.ordering.auth.service.CaptchaService;
import com.hbue.ordering.auth.service.LoginAttemptService;
import com.hbue.ordering.auth.service.JwtTokenService;
import com.hbue.ordering.auth.service.RefreshTokenService;
import com.hbue.ordering.auth.service.RefreshTokenLockService;
import com.hbue.ordering.auth.vo.LoginVO;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.user.api.client.UserServiceClient;
import com.hbue.ordering.user.api.dto.UserPasswordVerifyCommand;
import com.hbue.ordering.user.api.dto.UserRegisterCommand;
import com.hbue.ordering.user.api.vo.UserBasicVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/**
 * 认证业务服务实现。
 *
 * <p>负责认证流程编排：账号资料与密码校验委托给 user-service，访问令牌由
 * RSA 私钥签发，Refresh Token、Access Token 黑名单及登录失败锁定状态由 Redis
 * 服务维护。本类不持有用户表数据，也不把密码或完整令牌写入日志。</p>
 *
 * @author order-system
 * @date 2026-09-21
 */
@Service
public class AuthServiceImpl implements AuthService {

    /**
     * 当前类的日志对象。
     */
    private static final Logger log =
            LoggerFactory.getLogger(AuthServiceImpl.class);

    /**
     * 用户服务远程调用客户端。
     */
    private final UserServiceClient userServiceClient;

    /**
     * 登录失败计数与账号临时锁定服务。
     */
    private final LoginAttemptService loginAttemptService;

    /**
     * 图片验证码服务。
     */
    private final CaptchaService captchaService;

    /**
     * JWT Token 服务。
     */
    private final JwtTokenService jwtTokenService;

    /**
     * Refresh Token 服务。
     */
    private final RefreshTokenService refreshTokenService;

    /**
     * Access Token 黑名单服务。
     */
    private final AccessTokenBlacklistService accessTokenBlacklistService;

    /**
     * Refresh Token 轮换分布式锁服务。
     */
    private final RefreshTokenLockService refreshTokenLockService;

    /**
     * 创建认证业务服务。
     *
     * @param userServiceClient 用户服务远程调用客户端
     * @param loginAttemptService 登录失败计数与账号临时锁定服务
     * @param captchaService 图片验证码服务
     * @param jwtTokenService JWT Token 服务
     * @param refreshTokenService Refresh Token 服务
     * @param accessTokenBlacklistService Access Token 黑名单服务
     * @param refreshTokenLockService Refresh Token 轮换分布式锁服务
     */
    public AuthServiceImpl(
            UserServiceClient userServiceClient,
            LoginAttemptService loginAttemptService,
            CaptchaService captchaService,
            JwtTokenService jwtTokenService,
            RefreshTokenService refreshTokenService,
            AccessTokenBlacklistService accessTokenBlacklistService,
            RefreshTokenLockService refreshTokenLockService
    ) {
        this.userServiceClient = userServiceClient;
        this.loginAttemptService = loginAttemptService;
        this.captchaService = captchaService;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
        this.accessTokenBlacklistService = accessTokenBlacklistService;
        this.refreshTokenLockService = refreshTokenLockService;
    }

    /**
     * 用户登录。
     *
     * @param request 登录请求
     * @return 登录 Token
     * @throws BusinessException 账号已锁定、凭据无效、依赖服务不可用或 Token 存储失败时抛出
     */
    @Override
    public LoginVO login(LoginRequest request) {
        // 先校验图片验证码：验证失败时立即返回，不触发任何账号级状态变更。
        // 验证码失败不能计入登录失败次数，否则攻击者可用他人用户名刷错验证码把账号锁死。
        if (!captchaService.verify(
                request.getCaptchaId(),
                request.getCaptchaCode()
        )) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "验证码错误或已过期"
            );
        }

        // 在调用用户服务前检查账号锁定状态，锁定期间不再执行密码校验。
        if (loginAttemptService.isLocked(request.getAccount().trim().toLowerCase(java.util.Locale.ROOT))) {
            throw new BusinessException(
                    CommonErrorCode.TOO_MANY_REQUESTS,
                    "登录失败次数过多，请稍后重试"
            );
        }

        // 创建用户密码校验命令。
        UserPasswordVerifyCommand command =
                new UserPasswordVerifyCommand();

        // 设置登录用户名。
        command.setAccount(request.getAccount().trim().toLowerCase(java.util.Locale.ROOT));

        // 设置登录密码。
        command.setPassword(request.getPassword());

        // 调用用户服务校验账号密码。
        ApiResponse<UserBasicVO> response =
                userServiceClient.authenticate(command);

        // 用户服务无响应、发生内部错误或触发降级时统一返回 HTTP 503。
        if (response == null
                || CommonErrorCode.INTERNAL_ERROR.code()
                .equals(response.getCode())
                || CommonErrorCode.SERVICE_UNAVAILABLE.code()
                .equals(response.getCode())) {
            throw new BusinessException(
                    CommonErrorCode.SERVICE_UNAVAILABLE,
                    "用户服务暂时不可用"
            );
        }

        // 用户服务明确拒绝认证或没有返回用户信息时，记录本次账号级失败次数。
        if (CommonErrorCode.UNAUTHORIZED.code().equals(response.getCode())
                || response.getData() == null) {
            // 达到配置阈值后返回 HTTP 429，后续请求会在调用用户服务前被锁定检查拦截。
            if (loginAttemptService.recordFailure(request.getAccount().trim().toLowerCase(java.util.Locale.ROOT))) {
                throw new BusinessException(
                        CommonErrorCode.TOO_MANY_REQUESTS,
                        "登录失败次数过多，请稍后重试"
                );
            }

            // 未达到锁定阈值时仍使用普通认证失败响应，不泄露账号是否存在。
            throw new BusinessException(
                    CommonErrorCode.UNAUTHORIZED,
                    "账号或密码错误"
            );
        }

        // 获取认证成功的用户信息。
        UserBasicVO userBasicVO = response.getData();

        // 凭据校验成功后清除历史失败和锁定状态，新的失败窗口从零开始。
        loginAttemptService.recordSuccess(request.getAccount().trim().toLowerCase(java.util.Locale.ROOT));

        // 使用 RSA 私钥签发包含角色信息的 JWT。
        String accessToken = jwtTokenService.createAccessToken(
                userBasicVO.getUserUuid(),
                userBasicVO.getUsername(),
                userBasicVO.getRoleCode()
        );

        // 将 Refresh Token 保存到 Redis，供后续换取新的访问 Token。
        String refreshToken = refreshTokenService.createRefreshToken(
                userBasicVO.getUserId(),
                userBasicVO.getUsername(),
                userBasicVO.getRoleCode()
        );

        // 返回登录 Token。
        return LoginVO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(
                        jwtTokenService.getAccessTokenTtlSeconds()
                )
                .refreshExpiresIn(
                        refreshTokenService.getRefreshTokenTtlSeconds()
                )
                .build();
    }

    /**
     * 使用 Refresh Token 刷新登录 Token。
     *
     * @param request Refresh Token 刷新请求
     * @return 新的登录 Token
     * @throws BusinessException Refresh Token 无效、已被并发消费或锁/存储服务不可用时抛出
     */
    @Override
    public LoginVO refreshAccessToken(RefreshTokenRequest request) {
        // 固定本次请求使用的令牌，确保锁 Key 和 Redis 查询针对同一令牌。
        String oldRefreshToken = request.getRefreshToken();

        // 同一枚旧 Refresh Token 必须先获取同一把分布式锁，再读取 Redis 状态。
        try (RefreshTokenLockService.LockHandle ignored =
                     refreshTokenLockService.acquire(
                             oldRefreshToken
                     )) {
            // 持锁期间完成校验和轮换；原子消费负责锁租约到期后的最终并发保护。
            return refreshAccessTokenWithLock(oldRefreshToken);
        }
    }

    /**
     * 在已经获取 Refresh Token 分布式锁后完成令牌轮换。
     *
     * @param oldRefreshToken 原 Refresh Token
     * @return 新的登录 Token
     * @throws BusinessException Refresh Token 无效、并发消费失败或依赖服务不可用时抛出
     */
    private LoginVO refreshAccessTokenWithLock(
            String oldRefreshToken
    ) {
        // 查询 Redis 中保存的 Refresh Token 用户信息。
        RefreshTokenInfo refreshTokenInfo =
                refreshTokenService.getRefreshTokenInfo(
                        oldRefreshToken
                );

        // Token 不存在或已过期时，拒绝刷新请求。
        if (refreshTokenInfo == null) {
            throw new BusinessException(
                    CommonErrorCode.UNAUTHORIZED,
                    "Refresh Token 无效或已过期"
            );
        }

        // 从用户服务取得当前公开身份和账号状态，不能从旧令牌派生内部 ID 公开出去。
        ApiResponse<UserBasicVO> identityResponse = userServiceClient.getUserById(refreshTokenInfo.getUserId());
        if (identityResponse == null || !"COMMON_000".equals(identityResponse.getCode()) || identityResponse.getData() == null
                || !refreshTokenInfo.getUserId().equals(identityResponse.getData().getUserId())) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "用户身份不可用，请重新登录");
        }
        UserBasicVO currentIdentity = identityResponse.getData();
        // 使用用户信息签发新的访问 Token。
        String accessToken = jwtTokenService.createAccessToken(
                currentIdentity.getUserUuid(),
                currentIdentity.getUsername(),
                currentIdentity.getRoleCode()
        );

        // 创建新的 Refresh Token，实行刷新令牌轮换。
        String newRefreshToken = refreshTokenService.createRefreshToken(
                refreshTokenInfo.getUserId(),
                currentIdentity.getUsername(),
                currentIdentity.getRoleCode()
        );

        try {
            // Redis 对单个 Key 的 DEL 是原子操作，用作锁失效后的最终消费校验。
            boolean oldTokenConsumed =
                    refreshTokenService.consumeRefreshToken(
                            oldRefreshToken
                    );

            // 并发请求已消费旧 Token 时，清理本次预创建的新 Token 并拒绝重复轮换。
            if (!oldTokenConsumed) {
                throw new BusinessException(
                        CommonErrorCode.UNAUTHORIZED,
                        "Refresh Token 无效或已过期"
                );
            }
        } catch (BusinessException exception) {
            // 消费失败时，新 Token 尚未返回给客户端，应尽力清理避免无主记录。
            deleteUncommittedRefreshToken(newRefreshToken);
            throw exception;
        }

        // 返回新的访问 Token 和 Refresh Token。
        return LoginVO.builder()
                .accessToken(accessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(
                        jwtTokenService.getAccessTokenTtlSeconds()
                )
                .refreshExpiresIn(
                        refreshTokenService.getRefreshTokenTtlSeconds()
                )
                .build();
    }

    /**
     * 尽力删除没有返回给客户端的新 Refresh Token。
     *
     * @param refreshToken 尚未提交的新 Refresh Token
     */
    private void deleteUncommittedRefreshToken(String refreshToken) {
        try {
            // 清理本次刷新未能提交的新令牌。
            refreshTokenService.deleteRefreshToken(refreshToken);
        } catch (RuntimeException exception) {
            // 清理失败不能覆盖原始认证结果；令牌自身仍受 Redis TTL 限制。
            log.warn("清理未提交的 Refresh Token 失败", exception);
        }
    }

    /**
     * 用户注销。
     *
     * @param request 注销请求，包含需要撤销的 Refresh Token
     * @param accessToken 当前已通过认证的 Access Token
     * @throws BusinessException Access Token 不完整、Refresh Token 属于其他用户或 Redis 撤销失败时抛出
     */
    @Override
    public void logout(
            RefreshTokenRequest request,
            Jwt accessToken
    ) {
        // 只允许使用已认证且包含必要声明的访问令牌执行注销。
        if (accessToken == null
                || accessToken.getSubject() == null
                || accessToken.getId() == null
                || accessToken.getExpiresAt() == null) {
            throw new BusinessException(
                    CommonErrorCode.UNAUTHORIZED,
                    "当前访问令牌无效"
            );
        }

        // 查询 Refresh Token 所属用户，阻止用户注销他人的会话。
        RefreshTokenInfo refreshTokenInfo =
                refreshTokenService.getRefreshTokenInfo(
                        request.getRefreshToken()
                );
        if (refreshTokenInfo != null
                && !String.valueOf(refreshTokenInfo.getUserId())
                .equals(accessToken.getSubject())) {
            throw new BusinessException(
                    CommonErrorCode.FORBIDDEN,
                    "不能注销其他用户的 Refresh Token"
            );
        }

        // 先撤销当前访问令牌，避免 Refresh Token 删除失败时仍可使用 Access Token。
        accessTokenBlacklistService.blacklist(
                accessToken.getId(),
                accessToken.getExpiresAt()
        );

        // 删除当前用户的 Refresh Token，阻止其继续换取新的访问令牌。
        refreshTokenService.deleteRefreshToken(
                request.getRefreshToken()
        );
    }

    /**
     * 用户注册。
     *
     * @param request 注册请求
     * @return 注册成功的用户基础信息
     * @throws BusinessException 注册信息冲突或用户服务不可用时抛出
     */
    @Override
    public UserBasicVO register(RegisterRequest request) {
        // 创建用户注册远程调用命令。
        UserRegisterCommand command =
                new UserRegisterCommand();

        // 设置注册用户名。
        command.setUsername(request.getUsername());

        // 设置注册邮箱。
        command.setEmail(request.getEmail().trim().toLowerCase(java.util.Locale.ROOT));

        // 设置注册密码。
        command.setPassword(request.getPassword());

        // 调用用户服务完成注册。
        ApiResponse<UserBasicVO> response =
                userServiceClient.register(command);

        // 用户服务没有返回响应时，认为服务不可用。
        if (response == null) {
            throw new BusinessException(
                    CommonErrorCode.INTERNAL_ERROR,
                    "用户服务暂时不可用"
            );
        }

        // 用户服务发生系统异常时，返回系统异常。
        if (CommonErrorCode.INTERNAL_ERROR.code()
                .equals(response.getCode())) {
            throw new BusinessException(
                    CommonErrorCode.INTERNAL_ERROR,
                    "用户服务暂时不可用"
            );
        }

        // 用户名或邮箱重复时，返回参数错误。
        if (CommonErrorCode.BAD_REQUEST.code()
                .equals(response.getCode())) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    response.getMessage()
            );
        }

        // 注册成功但没有返回用户数据时，认为响应异常。
        if (response.getData() == null) {
            throw new BusinessException(
                    CommonErrorCode.INTERNAL_ERROR,
                    "注册结果异常"
            );
        }

        // 返回注册成功的用户基础信息。
        return response.getData();
    }
}
