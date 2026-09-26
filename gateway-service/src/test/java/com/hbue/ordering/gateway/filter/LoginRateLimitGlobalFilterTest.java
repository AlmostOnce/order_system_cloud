package com.hbue.ordering.gateway.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Gateway 登录接口限流全局过滤器测试。
 *
 * <p>验证登录请求按照客户端 IP 限流，
 * 并验证限流拒绝和 Redis 故障时返回项目统一响应。</p>
 *
 * @author order-system
 * @date 2026-09-25
 */
class LoginRateLimitGlobalFilterTest {

    /**
     * 验证登录请求超过限流阈值时返回 HTTP 429 和统一错误响应。
     *
     * @throws Exception JSON 响应解析失败时抛出
     */
    @Test
    void shouldReturn429WhenLoginRequestIsLimited() throws Exception {
        RedisRateLimiter redisRateLimiter = mock(RedisRateLimiter.class);
        when(redisRateLimiter.isAllowed("auth-login", "198.51.100.8"))
                .thenReturn(Mono.just(
                        new RateLimiter.Response(
                                false,
                                Map.of(
                                        "X-RateLimit-Replenish-Rate",
                                        "1"
                                )
                        )
                ));
        LoginRateLimitGlobalFilter filter = new LoginRateLimitGlobalFilter(
                redisRateLimiter,
                new ObjectMapper()
        );
        MockServerWebExchange exchange = createLoginExchange();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = currentExchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertEquals(
                HttpStatus.TOO_MANY_REQUESTS,
                exchange.getResponse().getStatusCode()
        );
        assertFalse(chainCalled.get());
        JsonNode responseBody = new ObjectMapper().readTree(
                exchange.getResponse().getBodyAsString().block()
        );
        assertEquals("COMMON_429", responseBody.get("code").asText());
        assertEquals(
                "1",
                exchange.getResponse().getHeaders()
                        .getFirst("X-RateLimit-Replenish-Rate")
        );
        verify(redisRateLimiter)
                .isAllowed("auth-login", "198.51.100.8");
    }

    /**
     * 验证令牌桶允许的登录请求会继续进入认证服务路由。
     */
    @Test
    void shouldContinueGatewayChainWhenLoginRequestIsAllowed() {
        RedisRateLimiter redisRateLimiter = mock(RedisRateLimiter.class);
        when(redisRateLimiter.isAllowed("auth-login", "198.51.100.8"))
                .thenReturn(Mono.just(
                        new RateLimiter.Response(true, Map.of())
                ));
        LoginRateLimitGlobalFilter filter = new LoginRateLimitGlobalFilter(
                redisRateLimiter,
                new ObjectMapper()
        );
        MockServerWebExchange exchange = createLoginExchange();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = currentExchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get());
        verify(redisRateLimiter)
                .isAllowed("auth-login", "198.51.100.8");
    }

    /**
     * 验证 Redis 限流服务故障时拒绝登录并返回统一 HTTP 503 响应。
     *
     * @throws Exception JSON 响应解析失败时抛出
     */
    @Test
    void shouldFailClosedWhenRedisRateLimiterIsUnavailable()
            throws Exception {
        RedisRateLimiter redisRateLimiter = mock(RedisRateLimiter.class);
        when(redisRateLimiter.isAllowed("auth-login", "198.51.100.8"))
                .thenReturn(Mono.error(
                        new IllegalStateException("Redis is unavailable")
                ));
        LoginRateLimitGlobalFilter filter = new LoginRateLimitGlobalFilter(
                redisRateLimiter,
                new ObjectMapper()
        );
        MockServerWebExchange exchange = createLoginExchange();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = currentExchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                exchange.getResponse().getStatusCode()
        );
        assertFalse(chainCalled.get());
        JsonNode responseBody = new ObjectMapper().readTree(
                exchange.getResponse().getBodyAsString().block()
        );
        assertEquals("COMMON_503", responseBody.get("code").asText());
    }

    /**
     * 验证注册等非登录请求不消耗登录接口的限流额度。
     */
    @Test
    void shouldNotApplyLoginLimitToOtherAuthEndpoints() {
        RedisRateLimiter redisRateLimiter = mock(RedisRateLimiter.class);
        LoginRateLimitGlobalFilter filter = new LoginRateLimitGlobalFilter(
                redisRateLimiter,
                new ObjectMapper()
        );
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/register")
                        .remoteAddress(new InetSocketAddress(
                                "198.51.100.8",
                                50000
                        ))
        );
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = currentExchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get());
        verifyNoInteractions(redisRateLimiter);
    }

    /**
     * 验证伪造的 X-Forwarded-For 不会覆盖 TCP 连接的客户端 IP。
     */
    @Test
    void shouldUseRemoteAddressInsteadOfForwardedHeader() {
        RedisRateLimiter redisRateLimiter = mock(RedisRateLimiter.class);
        when(redisRateLimiter.isAllowed("auth-login", "198.51.100.8"))
                .thenReturn(Mono.just(
                        new RateLimiter.Response(true, Map.of())
                ));
        LoginRateLimitGlobalFilter filter = new LoginRateLimitGlobalFilter(
                redisRateLimiter,
                new ObjectMapper()
        );
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login")
                        .header("X-Forwarded-For", "203.0.113.99")
                        .remoteAddress(new InetSocketAddress(
                                "198.51.100.8",
                                50000
                        ))
        );

        filter.filter(exchange, currentExchange -> Mono.empty()).block();

        verify(redisRateLimiter)
                .isAllowed("auth-login", "198.51.100.8");
    }

    /**
     * 创建一个带有固定客户端 IP 的登录请求交换对象。
     *
     * @return 登录请求交换对象
     */
    private MockServerWebExchange createLoginExchange() {
        // 固定测试 IP，使测试能够明确验证限流 Key 的来源。
        return MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login")
                        .header("X-Forwarded-For", "203.0.113.99")
                        .remoteAddress(new InetSocketAddress(
                                "198.51.100.8",
                                50000
                        ))
        );
    }
}
