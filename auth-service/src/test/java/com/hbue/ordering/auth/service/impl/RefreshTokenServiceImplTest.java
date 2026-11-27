package com.hbue.ordering.auth.service.impl;

import com.hbue.ordering.auth.model.RefreshTokenInfo;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.redis.RedisOperationsService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Refresh Token Redis 服务测试，覆盖令牌写入、会话绝对期限与会话索引淘汰。
 *
 * @author order-system
 * @date 2026-09-22
 */
class RefreshTokenServiceImplTest {

    /**
     * 测试用单枚令牌有效期，30 天。
     */
    private static final long TTL_SECONDS = 2592000L;

    /**
     * 测试用会话绝对有效期，30 天。
     */
    private static final long ABSOLUTE_SECONDS = 2592000L;

    /**
     * 测试用同一用户并发会话上限。
     */
    private static final int MAX_SESSIONS = 5;

    /**
     * 验证创建 Refresh Token 后能够保存用户信息、首次签发时间并设置过期时间。
     */
    @Test
    void shouldStoreRefreshTokenWithExpiration() {
        RedisOperationsService redisOperationsService = mock(RedisOperationsService.class);
        // 令牌与索引都需要设置过期时间，索引写入与数量统计也按正常链路返回。
        when(redisOperationsService.expire(anyString(), any(Duration.class))).thenReturn(true);
        when(redisOperationsService.addToSortedSet(anyString(), anyString(), anyDouble())).thenReturn(true);
        when(redisOperationsService.sortedSetSize(anyString())).thenReturn(1L);

        RefreshTokenServiceImpl refreshTokenService = newService(redisOperationsService);

        long before = System.currentTimeMillis();
        String refreshToken = refreshTokenService.createRefreshToken(
                5L,
                "测试用户",
                "CUSTOMER"
        );
        long after = System.currentTimeMillis();

        assertFalse(refreshToken.isBlank());
        assertTrue(refreshToken.length() >= 32);

        String redisKey = "auth:refresh-token:" + refreshToken;
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> valuesCaptor = ArgumentCaptor.forClass(Map.class);
        // 确认用户信息写入封装组件。
        verify(redisOperationsService).putHashValues(eq(redisKey), valuesCaptor.capture());
        Map<String, String> stored = valuesCaptor.getValue();
        assertEquals("5", stored.get("userId"));
        assertEquals("测试用户", stored.get("username"));
        assertEquals("CUSTOMER", stored.get("roleCode"));
        assertEquals("0", stored.get("credential_version"));
        // 登录即新会话：首次签发时间应落在本次调用的时间窗口内。
        long issuedAt = Long.parseLong(stored.get("issued_at"));
        assertTrue(issuedAt >= before && issuedAt <= after, "首次签发时间应在本次调用时间窗口内");

        // 单枚令牌按配置的 30 天过期，并按签发时间登记到该用户的会话索引。
        verify(redisOperationsService).expire(redisKey, Duration.ofSeconds(TTL_SECONDS));
        verify(redisOperationsService).addToSortedSet(
                "auth:refresh-sessions:5", refreshToken, (double) issuedAt);
        verify(redisOperationsService).expire(
                "auth:refresh-sessions:5", Duration.ofSeconds(ABSOLUTE_SECONDS));
    }

    /**
     * 验证轮换后的令牌 TTL 受会话剩余绝对时间限制，不会把会话整体续命。
     */
    @Test
    void shouldCapTokenTtlByAbsoluteSessionRemainingTime() {
        RedisOperationsService redisOperationsService = mock(RedisOperationsService.class);
        when(redisOperationsService.expire(anyString(), any(Duration.class))).thenReturn(true);
        when(redisOperationsService.addToSortedSet(anyString(), anyString(), anyDouble())).thenReturn(true);
        when(redisOperationsService.sortedSetSize(anyString())).thenReturn(1L);

        // 绝对有效期 600 秒，会话已在 500 秒前签发，剩余约 100 秒。
        RefreshTokenServiceImpl refreshTokenService =
                new RefreshTokenServiceImpl(redisOperationsService, TTL_SECONDS, 600L, MAX_SESSIONS);
        long sessionStart = System.currentTimeMillis() - 500_000L;

        String refreshToken = refreshTokenService.createRefreshToken(
                5L, "测试用户", "CUSTOMER", 0L, sessionStart);

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(redisOperationsService).expire(
                eq("auth:refresh-token:" + refreshToken), ttlCaptor.capture());
        long ttlSeconds = ttlCaptor.getValue().toSeconds();
        // 不能超过剩余绝对时间，也不能退化为零或负数（否则令牌写出即过期）。
        assertTrue(ttlSeconds <= 100L && ttlSeconds >= 95L, "TTL 应按会话剩余时间收敛，实际=" + ttlSeconds);
    }

    /**
     * 验证超过会话绝对期限时拒绝继续签发，即使单枚令牌尚未到期。
     */
    @Test
    void shouldRejectExpiredAbsoluteSession() {
        RedisOperationsService redisOperationsService = mock(RedisOperationsService.class);
        RefreshTokenServiceImpl refreshTokenService =
                new RefreshTokenServiceImpl(redisOperationsService, TTL_SECONDS, 600L, MAX_SESSIONS);
        long expiredSessionStart = System.currentTimeMillis() - 700_000L;

        BusinessException exception = assertThrows(BusinessException.class,
                () -> refreshTokenService.createRefreshToken(
                        5L, "测试用户", "CUSTOMER", 0L, expiredSessionStart));

        assertEquals(CommonErrorCode.UNAUTHORIZED, exception.getErrorCode());
        // 会话整体到期时不能再写入任何令牌或索引。
        verify(redisOperationsService, never()).putHashValues(anyString(), any());
    }

    /**
     * 验证同一用户会话数超过上限时淘汰最早的一枚，并同步删除其令牌。
     */
    @Test
    void shouldEvictEarliestSessionWhenLimitExceeded() {
        RedisOperationsService redisOperationsService = mock(RedisOperationsService.class);
        when(redisOperationsService.expire(anyString(), any(Duration.class))).thenReturn(true);
        when(redisOperationsService.addToSortedSet(anyString(), anyString(), anyDouble())).thenReturn(true);
        // 写入后集合中有 6 枚会话，超过上限 1 枚。
        when(redisOperationsService.sortedSetSize("auth:refresh-sessions:5")).thenReturn(6L);
        when(redisOperationsService.sortedSetRange("auth:refresh-sessions:5", 0L, 0L))
                .thenReturn(List.of("oldest-token"));

        RefreshTokenServiceImpl refreshTokenService = newService(redisOperationsService);
        String newToken = refreshTokenService.createRefreshToken(5L, "测试用户", "CUSTOMER", 0L);

        // 被淘汰的会话必须立即失效：删除令牌 Key 并从索引移除。
        verify(redisOperationsService).delete("auth:refresh-token:oldest-token");
        verify(redisOperationsService).removeFromSortedSet("auth:refresh-sessions:5", "oldest-token");
        // 新签发的令牌不能进入淘汰列表。
        assertNotEquals("oldest-token", newToken);
    }

    /**
     * 验证会话索引写入失败时拒绝签发令牌，并清理已经写入的令牌数据。
     */
    @Test
    void shouldRejectTokenWhenSessionIndexCannotBeWritten() {
        RedisOperationsService redisOperationsService = mock(RedisOperationsService.class);
        when(redisOperationsService.expire(anyString(), any(Duration.class))).thenReturn(true);
        // ZADD 无法确认写入（例如 Redis 返回空结果）。
        when(redisOperationsService.addToSortedSet(anyString(), anyString(), anyDouble())).thenReturn(null);

        RefreshTokenServiceImpl refreshTokenService = newService(redisOperationsService);

        assertThrows(IllegalStateException.class,
                () -> refreshTokenService.createRefreshToken(5L, "测试用户", "CUSTOMER", 0L));
        verify(redisOperationsService).delete(startsWith("auth:refresh-token:"));
    }

    /**
     * 验证无法设置过期时间时会删除刚写入的数据并拒绝签发令牌。
     */
    @Test
    void shouldDeleteStoredDataWhenExpirationCannotBeSet() {
        RedisOperationsService redisOperationsService = mock(RedisOperationsService.class);
        when(redisOperationsService.expire(anyString(), any(Duration.class))).thenReturn(false);

        RefreshTokenServiceImpl refreshTokenService = newService(redisOperationsService);

        assertThrows(IllegalStateException.class,
                () -> refreshTokenService.createRefreshToken(5L, "测试用户", "CUSTOMER"));

        // 过期设置失败时必须清理数据，避免留下永久有效的令牌记录。
        verify(redisOperationsService).delete(startsWith("auth:refresh-token:"));
    }

    /**
     * 验证可通过 Redis 基础操作组件读取令牌对应的用户信息，旧令牌的首次签发时间按未知返回 0。
     */
    @Test
    void shouldReadRefreshTokenInfoThroughRedisOperationsService() {
        RedisOperationsService redisOperationsService = mock(RedisOperationsService.class);
        Map<Object, Object> values = new HashMap<>();
        values.put("userId", "5");
        values.put("username", "测试用户");
        values.put("roleCode", "CUSTOMER");
        when(redisOperationsService.getHashValues("auth:refresh-token:sample-token"))
                .thenReturn(values);

        RefreshTokenServiceImpl refreshTokenService = newService(redisOperationsService);

        RefreshTokenInfo refreshTokenInfo = refreshTokenService.getRefreshTokenInfo("sample-token");

        assertEquals(5L, refreshTokenInfo.getUserId());
        assertEquals("测试用户", refreshTokenInfo.getUsername());
        assertEquals("CUSTOMER", refreshTokenInfo.getRoleCode());
        // 旧版本写入的令牌没有 issued_at 字段，按未知处理以兼容升级。
        assertEquals(0L, refreshTokenInfo.getIssuedAtMillis());
        verify(redisOperationsService).getHashValues("auth:refresh-token:sample-token");
    }

    /**
     * 验证会话首次签发时间可被读取，损坏的值按未知处理而不是抛异常。
     */
    @Test
    void shouldReadIssuedAtAndTolerateCorruptedValue() {
        RedisOperationsService redisOperationsService = mock(RedisOperationsService.class);
        Map<Object, Object> values = new HashMap<>();
        values.put("userId", "5");
        values.put("username", "测试用户");
        values.put("roleCode", "CUSTOMER");
        values.put("issued_at", "1700000000000");
        when(redisOperationsService.getHashValues("auth:refresh-token:valid"))
                .thenReturn(values);
        Map<Object, Object> corrupted = new HashMap<>(values);
        corrupted.put("issued_at", "not-a-number");
        when(redisOperationsService.getHashValues("auth:refresh-token:corrupted"))
                .thenReturn(corrupted);

        RefreshTokenServiceImpl refreshTokenService = newService(redisOperationsService);

        assertEquals(1700000000000L,
                refreshTokenService.getRefreshTokenInfo("valid").getIssuedAtMillis());
        assertEquals(0L, refreshTokenService.getRefreshTokenInfo("corrupted").getIssuedAtMillis());
    }

    /**
     * 验证注销删除令牌时同步清理该用户的会话索引。
     */
    @Test
    void shouldRemoveSessionIndexWhenDeletingToken() {
        RedisOperationsService redisOperationsService = mock(RedisOperationsService.class);
        Map<Object, Object> values = new HashMap<>();
        values.put("userId", "5");
        when(redisOperationsService.getHashValues("auth:refresh-token:sample-token"))
                .thenReturn(values);

        RefreshTokenServiceImpl refreshTokenService = newService(redisOperationsService);
        refreshTokenService.deleteRefreshToken("sample-token");

        verify(redisOperationsService).removeFromSortedSet("auth:refresh-sessions:5", "sample-token");
        verify(redisOperationsService).delete("auth:refresh-token:sample-token");
    }

    /**
     * 验证只有 Redis 确认删除旧令牌时，刷新令牌才被视为成功消费。
     */
    @Test
    void shouldConsumeRefreshTokenOnlyWhenRedisDeletesItsKey() {
        RedisOperationsService redisOperationsService = mock(RedisOperationsService.class);
        when(redisOperationsService.delete("auth:refresh-token:sample-token")).thenReturn(true);
        RefreshTokenServiceImpl refreshTokenService = newService(redisOperationsService);

        boolean consumed = refreshTokenService.consumeRefreshToken("sample-token");

        assertTrue(consumed);
        verify(redisOperationsService).delete("auth:refresh-token:sample-token");
    }

    /**
     * 验证并发请求已删除旧令牌时，后续请求不能再次消费成功。
     */
    @Test
    void shouldReturnFalseWhenRefreshTokenWasAlreadyConsumed() {
        RedisOperationsService redisOperationsService = mock(RedisOperationsService.class);
        when(redisOperationsService.delete("auth:refresh-token:sample-token")).thenReturn(false);
        RefreshTokenServiceImpl refreshTokenService = newService(redisOperationsService);

        boolean consumed = refreshTokenService.consumeRefreshToken("sample-token");

        assertFalse(consumed);
        verify(redisOperationsService).delete("auth:refresh-token:sample-token");
    }

    /**
     * 验证空 Refresh Token 不会触发 Redis 操作。
     */
    @Test
    void shouldSkipRedisDeleteWhenRefreshTokenIsBlank() {
        RedisOperationsService redisOperationsService = mock(RedisOperationsService.class);
        RefreshTokenServiceImpl refreshTokenService = newService(redisOperationsService);

        refreshTokenService.deleteRefreshToken(" ");

        verifyNoInteractions(redisOperationsService);
    }

    /**
     * 创建使用测试默认配置的服务实例。
     *
     * @param redisOperationsService Redis 测试替身
     * @return 被测服务
     */
    private RefreshTokenServiceImpl newService(RedisOperationsService redisOperationsService) {
        return new RefreshTokenServiceImpl(
                redisOperationsService,
                TTL_SECONDS,
                ABSOLUTE_SECONDS,
                MAX_SESSIONS
        );
    }
}
