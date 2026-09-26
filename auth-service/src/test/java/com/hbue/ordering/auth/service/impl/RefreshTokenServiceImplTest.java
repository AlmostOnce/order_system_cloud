package com.hbue.ordering.auth.service.impl;

import com.hbue.ordering.auth.model.RefreshTokenInfo;
import com.hbue.ordering.common.redis.RedisOperationsService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Refresh Token Redis 服务测试。
 *
 * @author order-system
 * @date 2026-09-22
 */
class RefreshTokenServiceImplTest {

    /**
     * 验证创建 Refresh Token 后能够保存用户信息并设置过期时间。
     */
    @Test
    void shouldStoreRefreshTokenWithExpiration() {
        RedisOperationsService redisOperationsService =
                mock(RedisOperationsService.class);

        when(redisOperationsService.expire(
                anyString(),
                eq(Duration.ofSeconds(2592000L))
        )).thenReturn(true);

        RefreshTokenServiceImpl refreshTokenService =
                new RefreshTokenServiceImpl(
                        redisOperationsService,
                        2592000L
                );

        String refreshToken = refreshTokenService.createRefreshToken(
                5L,
                "测试用户",
                "CUSTOMER"
        );

        assertFalse(refreshToken.isBlank());
        assertTrue(refreshToken.length() >= 32);

        String redisKey = "auth:refresh-token:" + refreshToken;
        Map<String, String> expectedValues = new HashMap<>();
        expectedValues.put("userId", "5");
        expectedValues.put("username", "测试用户");
        expectedValues.put("roleCode", "CUSTOMER");

        // 确认用户信息写入封装组件，且过期时间沿用原配置。
        verify(redisOperationsService)
                .putHashValues(redisKey, expectedValues);
        verify(redisOperationsService)
                .expire(redisKey, Duration.ofSeconds(2592000L));
    }

    /**
     * 验证无法设置过期时间时会删除刚写入的数据并拒绝签发令牌。
     */
    @Test
    void shouldDeleteStoredDataWhenExpirationCannotBeSet() {
        RedisOperationsService redisOperationsService =
                mock(RedisOperationsService.class);
        when(redisOperationsService.expire(
                anyString(),
                eq(Duration.ofSeconds(2592000L))
        )).thenReturn(false);

        RefreshTokenServiceImpl refreshTokenService =
                new RefreshTokenServiceImpl(
                        redisOperationsService,
                        2592000L
                );

        assertThrows(
                IllegalStateException.class,
                () -> refreshTokenService.createRefreshToken(
                        5L,
                        "测试用户",
                        "CUSTOMER"
                )
        );

        // 过期设置失败时必须清理数据，避免留下永久有效的令牌记录。
        verify(redisOperationsService).delete(
                org.mockito.ArgumentMatchers.startsWith(
                        "auth:refresh-token:"
                )
        );
    }

    /**
     * 验证可通过 Redis 基础操作组件读取令牌对应的用户信息。
     */
    @Test
    void shouldReadRefreshTokenInfoThroughRedisOperationsService() {
        RedisOperationsService redisOperationsService =
                mock(RedisOperationsService.class);
        Map<Object, Object> values = new HashMap<>();
        values.put("userId", "5");
        values.put("username", "测试用户");
        values.put("roleCode", "CUSTOMER");
        when(redisOperationsService.getHashValues(
                "auth:refresh-token:sample-token"
        )).thenReturn(values);

        RefreshTokenServiceImpl refreshTokenService =
                new RefreshTokenServiceImpl(
                        redisOperationsService,
                        2592000L
                );

        RefreshTokenInfo refreshTokenInfo =
                refreshTokenService.getRefreshTokenInfo(
                        "sample-token"
                );

        assertEquals(5L, refreshTokenInfo.getUserId());
        assertEquals("测试用户", refreshTokenInfo.getUsername());
        assertEquals("CUSTOMER", refreshTokenInfo.getRoleCode());
        verify(redisOperationsService)
                .getHashValues("auth:refresh-token:sample-token");
    }

    /**
     * 验证注销时通过 Redis 基础操作组件删除对应 Key。
     */
    @Test
    void shouldDeleteRefreshTokenThroughRedisOperationsService() {
        RedisOperationsService redisOperationsService =
                mock(RedisOperationsService.class);
        RefreshTokenServiceImpl refreshTokenService =
                new RefreshTokenServiceImpl(
                        redisOperationsService,
                        2592000L
                );

        refreshTokenService.deleteRefreshToken("sample-token");

        verify(redisOperationsService)
                .delete("auth:refresh-token:sample-token");
    }

    /**
     * 验证只有 Redis 确认删除旧令牌时，刷新令牌才被视为成功消费。
     */
    @Test
    void shouldConsumeRefreshTokenOnlyWhenRedisDeletesItsKey() {
        RedisOperationsService redisOperationsService =
                mock(RedisOperationsService.class);
        when(redisOperationsService.delete(
                "auth:refresh-token:sample-token"
        )).thenReturn(true);
        RefreshTokenServiceImpl refreshTokenService =
                new RefreshTokenServiceImpl(
                        redisOperationsService,
                        2592000L
                );

        boolean consumed = refreshTokenService.consumeRefreshToken(
                "sample-token"
        );

        assertTrue(consumed);
        verify(redisOperationsService)
                .delete("auth:refresh-token:sample-token");
    }

    /**
     * 验证并发请求已删除旧令牌时，后续请求不能再次消费成功。
     */
    @Test
    void shouldReturnFalseWhenRefreshTokenWasAlreadyConsumed() {
        RedisOperationsService redisOperationsService =
                mock(RedisOperationsService.class);
        when(redisOperationsService.delete(
                "auth:refresh-token:sample-token"
        )).thenReturn(false);
        RefreshTokenServiceImpl refreshTokenService =
                new RefreshTokenServiceImpl(
                        redisOperationsService,
                        2592000L
                );

        boolean consumed = refreshTokenService.consumeRefreshToken(
                "sample-token"
        );

        assertFalse(consumed);
        verify(redisOperationsService)
                .delete("auth:refresh-token:sample-token");
    }

    /**
     * 验证空 Refresh Token 不会触发 Redis 操作。
     */
    @Test
    void shouldSkipRedisDeleteWhenRefreshTokenIsBlank() {
        RedisOperationsService redisOperationsService =
                mock(RedisOperationsService.class);
        RefreshTokenServiceImpl refreshTokenService =
                new RefreshTokenServiceImpl(
                        redisOperationsService,
                        2592000L
                );

        refreshTokenService.deleteRefreshToken(" ");

        verifyNoInteractions(redisOperationsService);
    }
}
