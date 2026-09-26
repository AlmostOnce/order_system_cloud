package com.hbue.ordering.auth.service.impl;

import com.hbue.ordering.auth.dto.request.LoginRequest;
import com.hbue.ordering.auth.dto.request.RefreshTokenRequest;
import com.hbue.ordering.auth.model.RefreshTokenInfo;
import com.hbue.ordering.auth.service.AccessTokenBlacklistService;
import com.hbue.ordering.auth.service.JwtTokenService;
import com.hbue.ordering.auth.service.RefreshTokenLockService;
import com.hbue.ordering.auth.service.RefreshTokenService;
import com.hbue.ordering.auth.vo.LoginVO;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.user.api.client.UserServiceClient;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 认证业务服务测试。
 *
 * @author order-system
 * @date 2026-09-23
 */
class AuthServiceImplTest {

    /**
     * 验证用户服务返回认证失败时，认证服务向调用方返回未授权异常。
     */
    @Test
    void shouldReturnUnauthorizedWhenUserServiceRejectsCredentials() {
        UserServiceClient userServiceClient = mock(UserServiceClient.class);
        when(userServiceClient.authenticate(any())).thenReturn(
                ApiResponse.failure(
                        CommonErrorCode.UNAUTHORIZED,
                        "用户名或密码错误"
                )
        );

        AuthServiceImpl authService = createAuthService(userServiceClient);
        LoginRequest request = new LoginRequest();
        request.setUsername("test-user");
        request.setPassword("wrong-password");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.login(request)
        );

        assertEquals(
                CommonErrorCode.UNAUTHORIZED,
                exception.getErrorCode()
        );
    }

    /**
     * 验证用户服务不可用时，认证服务返回服务暂不可用异常。
     */
    @Test
    void shouldReturnServiceUnavailableWhenUserServiceIsUnavailable() {
        UserServiceClient userServiceClient = mock(UserServiceClient.class);
        when(userServiceClient.authenticate(any())).thenReturn(
                ApiResponse.failure(
                        CommonErrorCode.SERVICE_UNAVAILABLE,
                        "用户服务暂时不可用"
                )
        );

        AuthServiceImpl authService = createAuthService(userServiceClient);
        LoginRequest request = new LoginRequest();
        request.setUsername("test-user");
        request.setPassword("test-password");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.login(request)
        );

        assertEquals(
                CommonErrorCode.SERVICE_UNAVAILABLE,
                exception.getErrorCode()
        );
    }

    /**
     * 验证有效 Refresh Token 能够换取新的访问 Token 和 Refresh Token。
     */
    @Test
    void shouldRefreshTokensAndRotateOldRefreshToken() throws Exception {
        UserServiceClient userServiceClient = mock(UserServiceClient.class);
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        RefreshTokenService refreshTokenService =
                mock(RefreshTokenService.class);
        AccessTokenBlacklistService accessTokenBlacklistService =
                mock(AccessTokenBlacklistService.class);
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock distributedLock = mock(RLock.class);
        String expectedLockKey = "auth:refresh-token:lock:"
                + "66e4f4e9739a9ef9a9d6e414cfd05780c4ab0eb03e21fbf90ebf87e76d4db8f6";

        RefreshTokenInfo refreshTokenInfo = new RefreshTokenInfo(
                5L,
                "测试用户",
                "CUSTOMER"
        );

        when(refreshTokenService.getRefreshTokenInfo("old-refresh-token"))
                .thenReturn(refreshTokenInfo);
        when(jwtTokenService.createAccessToken(
                5L,
                "测试用户",
                "CUSTOMER"
        )).thenReturn("new-access-token");
        when(refreshTokenService.createRefreshToken(
                5L,
                "测试用户",
                "CUSTOMER"
        )).thenReturn("new-refresh-token");
        when(refreshTokenService.consumeRefreshToken("old-refresh-token"))
                .thenReturn(true);
        when(jwtTokenService.getAccessTokenTtlSeconds())
                .thenReturn(1800L);
        when(refreshTokenService.getRefreshTokenTtlSeconds())
                .thenReturn(2592000L);
        when(redissonClient.getLock(expectedLockKey))
                .thenReturn(distributedLock);
        when(distributedLock.tryLock(5L, 30L, TimeUnit.SECONDS))
                .thenReturn(true);
        when(distributedLock.isHeldByCurrentThread())
                .thenReturn(true);

        AuthServiceImpl authService = new AuthServiceImpl(
                userServiceClient,
                jwtTokenService,
                refreshTokenService,
                accessTokenBlacklistService,
                new RefreshTokenLockService(redissonClient)
        );

        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("old-refresh-token");

        LoginVO loginVO = authService.refreshAccessToken(request);

        assertEquals("new-access-token", loginVO.getAccessToken());
        assertEquals("new-refresh-token", loginVO.getRefreshToken());
        assertEquals(1800L, loginVO.getExpiresIn());
        assertEquals(2592000L, loginVO.getRefreshExpiresIn());

        // 分布式锁必须先于旧 Token 查询获取，并覆盖完整轮换与清理流程。
        org.mockito.InOrder inOrder = inOrder(
                redissonClient,
                distributedLock,
                refreshTokenService
        );
        inOrder.verify(redissonClient).getLock(expectedLockKey);
        inOrder.verify(distributedLock)
                .tryLock(5L, 30L, TimeUnit.SECONDS);
        inOrder.verify(refreshTokenService)
                .getRefreshTokenInfo("old-refresh-token");
        inOrder.verify(refreshTokenService)
                .consumeRefreshToken("old-refresh-token");
        inOrder.verify(distributedLock).isHeldByCurrentThread();
        inOrder.verify(distributedLock).unlock();
    }

    /**
     * 验证即使分布式锁意外失效，已被其它请求消费的旧令牌也不能再次轮换。
     */
    @Test
    void shouldRejectRefreshWhenOldTokenWasConsumedConcurrently() {
        UserServiceClient userServiceClient = mock(UserServiceClient.class);
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        RefreshTokenService refreshTokenService =
                mock(RefreshTokenService.class);
        AccessTokenBlacklistService accessTokenBlacklistService =
                mock(AccessTokenBlacklistService.class);
        RefreshTokenLockService refreshTokenLockService =
                mock(RefreshTokenLockService.class);
        when(refreshTokenLockService.acquire("old-refresh-token"))
                .thenReturn(mock(RefreshTokenLockService.LockHandle.class));
        when(refreshTokenService.getRefreshTokenInfo("old-refresh-token"))
                .thenReturn(new RefreshTokenInfo(
                        5L,
                        "测试用户",
                        "CUSTOMER"
                ));
        when(jwtTokenService.createAccessToken(
                5L,
                "测试用户",
                "CUSTOMER"
        )).thenReturn("new-access-token");
        when(refreshTokenService.createRefreshToken(
                5L,
                "测试用户",
                "CUSTOMER"
        )).thenReturn("orphan-refresh-token");
        when(refreshTokenService.consumeRefreshToken("old-refresh-token"))
                .thenReturn(false);

        AuthServiceImpl authService = new AuthServiceImpl(
                userServiceClient,
                jwtTokenService,
                refreshTokenService,
                accessTokenBlacklistService,
                refreshTokenLockService
        );
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("old-refresh-token");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.refreshAccessToken(request)
        );

        assertEquals(
                CommonErrorCode.UNAUTHORIZED,
                exception.getErrorCode()
        );
        verify(refreshTokenService)
                .deleteRefreshToken("orphan-refresh-token");
    }

    /**
     * 验证注销会先加入当前 Access Token 黑名单，再删除同一用户的 Refresh Token。
     */
    @Test
    void shouldBlacklistAccessTokenAndDeleteOwnedRefreshToken() {
        UserServiceClient userServiceClient = mock(UserServiceClient.class);
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        RefreshTokenService refreshTokenService =
                mock(RefreshTokenService.class);
        AccessTokenBlacklistService accessTokenBlacklistService =
                mock(AccessTokenBlacklistService.class);
        Instant expiresAt = Instant.now().plusSeconds(600L);

        when(refreshTokenService.getRefreshTokenInfo("refresh-token"))
                .thenReturn(new RefreshTokenInfo(
                        5L,
                        "测试用户",
                        "CUSTOMER"
                ));

        AuthServiceImpl authService = new AuthServiceImpl(
                userServiceClient,
                jwtTokenService,
                refreshTokenService,
                accessTokenBlacklistService,
                mock(RefreshTokenLockService.class)
        );
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("refresh-token");
        Jwt accessToken = createAccessToken("5", "jti-5", expiresAt);

        authService.logout(request, accessToken);

        org.mockito.InOrder inOrder = inOrder(
                accessTokenBlacklistService,
                refreshTokenService
        );
        inOrder.verify(accessTokenBlacklistService)
                .blacklist("jti-5", expiresAt);
        inOrder.verify(refreshTokenService)
                .deleteRefreshToken("refresh-token");
    }

    /**
     * 验证不能使用当前用户的 Access Token 注销其他用户的 Refresh Token。
     */
    @Test
    void shouldRejectLogoutWhenRefreshTokenBelongsToAnotherUser() {
        UserServiceClient userServiceClient = mock(UserServiceClient.class);
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        RefreshTokenService refreshTokenService =
                mock(RefreshTokenService.class);
        AccessTokenBlacklistService accessTokenBlacklistService =
                mock(AccessTokenBlacklistService.class);

        when(refreshTokenService.getRefreshTokenInfo("other-refresh-token"))
                .thenReturn(new RefreshTokenInfo(
                        8L,
                        "其他用户",
                        "CUSTOMER"
                ));

        AuthServiceImpl authService = new AuthServiceImpl(
                userServiceClient,
                jwtTokenService,
                refreshTokenService,
                accessTokenBlacklistService,
                mock(RefreshTokenLockService.class)
        );
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("other-refresh-token");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.logout(
                        request,
                        createAccessToken(
                                "5",
                                "jti-5",
                                Instant.now().plusSeconds(600L)
                        )
                )
        );
        assertEquals(
                CommonErrorCode.FORBIDDEN,
                exception.getErrorCode()
        );

        verify(accessTokenBlacklistService, never())
                .blacklist(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any());
        verify(refreshTokenService, never())
                .deleteRefreshToken("other-refresh-token");
    }

    /**
     * 创建含有用户 ID、jti 和有效期的访问令牌测试对象。
     *
     * @param userId JWT subject
     * @param tokenId JWT jti
     * @param expiresAt JWT 过期时间
     * @return 测试用 JWT
     */
    private Jwt createAccessToken(
            String userId,
            String tokenId,
            Instant expiresAt
    ) {
        // 构造资源服务器认证后会提供给 Controller 和 Service 的 JWT 主体。
        return Jwt.withTokenValue("access-token")
                .header("alg", "RS256")
                .subject(userId)
                .claim("jti", tokenId)
                .issuedAt(Instant.now())
                .expiresAt(expiresAt)
                .build();
    }

    /**
     * 创建认证业务服务测试对象。
     *
     * @param userServiceClient 用户服务远程调用客户端
     * @return 认证业务服务
     */
    private AuthServiceImpl createAuthService(
            UserServiceClient userServiceClient
    ) {
        // 登录失败测试不会执行 Token 服务，使用 Mockito 隔离外部依赖。
        return new AuthServiceImpl(
                userServiceClient,
                mock(JwtTokenService.class),
                mock(RefreshTokenService.class),
                mock(AccessTokenBlacklistService.class),
                mock(RefreshTokenLockService.class)
        );
    }
}
