package com.hbue.ordering.auth.service;

import com.hbue.ordering.common.redis.RedisOperationsService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Access Token 黑名单服务测试。
 *
 * @author order-system
 * @date 2026-09-25
 */
class AccessTokenBlacklistServiceTest {

    /**
     * 验证黑名单记录的有效期不超过 Access Token 的剩余有效期。
     */
    @Test
    void shouldExpireBlacklistEntryWhenAccessTokenExpires() {
        RedisOperationsService redisOperationsService =
                mock(RedisOperationsService.class);
        AccessTokenBlacklistService blacklistService =
                new AccessTokenBlacklistService(
                        redisOperationsService,
                        "auth:access-token:blacklist:"
                );
        Instant expiresAt = Instant.now().plusSeconds(120L);

        blacklistService.blacklist("jti-1", expiresAt);

        org.mockito.ArgumentCaptor<Duration> ttlCaptor =
                org.mockito.ArgumentCaptor.forClass(Duration.class);
        verify(redisOperationsService).setValue(
                eq("auth:access-token:blacklist:jti-1"),
                eq("1"),
                ttlCaptor.capture()
        );
        assertTrue(ttlCaptor.getValue().compareTo(Duration.ofSeconds(120L)) <= 0);
        assertTrue(ttlCaptor.getValue().compareTo(Duration.ZERO) > 0);
    }

    /**
     * 验证已经过期的访问令牌不会留下无意义的黑名单记录。
     */
    @Test
    void shouldNotWriteBlacklistEntryForExpiredToken() {
        RedisOperationsService redisOperationsService =
                mock(RedisOperationsService.class);
        AccessTokenBlacklistService blacklistService =
                new AccessTokenBlacklistService(
                        redisOperationsService,
                        "auth:access-token:blacklist:"
                );

        blacklistService.blacklist(
                "expired-jti",
                Instant.now().minusSeconds(1L)
        );

        verify(redisOperationsService, never())
                .setValue(any(), any(), any(Duration.class));
    }

    /**
     * 验证缺少 jti 时不会写入含糊的黑名单记录。
     */
    @Test
    void shouldRejectBlankTokenId() {
        RedisOperationsService redisOperationsService =
                mock(RedisOperationsService.class);
        AccessTokenBlacklistService blacklistService =
                new AccessTokenBlacklistService(
                        redisOperationsService,
                        "auth:access-token:blacklist:"
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> blacklistService.blacklist(
                        " ",
                        Instant.now().plusSeconds(120L)
                )
        );
        verify(redisOperationsService, never())
                .setValue(any(), any(), any(Duration.class));
    }
}
