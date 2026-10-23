package com.hbue.ordering.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.cors.reactive.CorsWebFilter;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** 验证浏览器预检、非白名单拒绝以及认证失败响应的跨域头。 */
class GatewayCorsConfigTest {

    private final CorsWebFilter filter = new CorsWebFilter(
            new GatewaySecurityConfig(null, null).corsConfigurationSource(
                    List.of("http://localhost:5173", "http://127.0.0.1:5173")
            )
    );

    @Test
    void shouldHandlePreflightBeforeAuthentication() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.options("http://localhost:9090/api/auth/captcha")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                "content-type,authorization,idempotency-key")
        );
        AtomicBoolean downstreamCalled = new AtomicBoolean();
        filter.filter(exchange, next -> {
            downstreamCalled.set(true);
            return next.getResponse().setComplete();
        }).block();

        assertFalse(downstreamCalled.get());
        assertEquals("http://localhost:5173", exchange.getResponse().getHeaders()
                .getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void shouldRejectUnlistedOrigin() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.options("http://localhost:9090/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://unlisted.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
        );
        AtomicBoolean downstreamCalled = new AtomicBoolean();
        filter.filter(exchange, next -> {
            downstreamCalled.set(true);
            return next.getResponse().setComplete();
        }).block();

        assertFalse(downstreamCalled.get());
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test
    void shouldPreserveCorsHeadersOnAuthenticationFailure() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost:9090/api/users/me")
                        .header(HttpHeaders.ORIGIN, "http://127.0.0.1:5173")
        );
        filter.filter(exchange, next -> {
            next.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return next.getResponse().setComplete();
        }).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertEquals("http://127.0.0.1:5173", exchange.getResponse().getHeaders()
                .getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
