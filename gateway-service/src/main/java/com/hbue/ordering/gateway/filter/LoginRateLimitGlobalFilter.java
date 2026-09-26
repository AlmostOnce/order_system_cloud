package com.hbue.ordering.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.error.ErrorCode;
import com.hbue.ordering.common.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 登录接口 IP 限流全局过滤器。
 *
 * <p>只限制客户端的 {@code POST /api/auth/login} 请求，
 * 使用 TCP 连接的远端地址作为 Redis 限流 Key，
 * 不信任客户端自行传入的 X-Forwarded-For 请求头。</p>
 *
 * <p>Redis 不可用或无法识别远端地址时采取 fail-closed 策略，
 * 返回 HTTP 503，避免限流失效后登录接口不受保护。</p>
 *
 * @author order-system
 * @date 2026-09-25
 */
@Component
public class LoginRateLimitGlobalFilter implements GlobalFilter, Ordered {

    /**
     * 登录请求路径。
     */
    private static final String LOGIN_PATH = "/api/auth/login";

    /**
     * Redis 限流器中登录入口使用的逻辑路由标识。
     */
    private static final String LOGIN_RATE_LIMIT_ID = "auth-login";

    /**
     * 日志记录器。
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(
            LoginRateLimitGlobalFilter.class
    );

    /**
     * Redis 令牌桶限流器。
     */
    private final RedisRateLimiter redisRateLimiter;

    /**
     * JSON 序列化工具。
     */
    private final ObjectMapper objectMapper;

    /**
     * 创建登录接口 IP 限流过滤器。
     *
     * @param redisRateLimiter Redis 令牌桶限流器
     * @param objectMapper JSON 序列化工具
     */
    public LoginRateLimitGlobalFilter(
            RedisRateLimiter redisRateLimiter,
            ObjectMapper objectMapper
    ) {
        this.redisRateLimiter = redisRateLimiter;
        this.objectMapper = objectMapper;
    }

    /**
     * 对登录接口执行 Redis 令牌桶限流。
     *
     * @param exchange 当前请求和响应交换对象
     * @param chain Gateway 过滤器链
     * @return 当前请求的响应式处理结果
     */
    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            GatewayFilterChain chain
    ) {
        // 注册、刷新、注销等请求不经过登录限流器。
        if (!isLoginRequest(exchange)) {
            return chain.filter(exchange);
        }

        // 只使用网络连接的远端地址，避免客户端伪造转发头绕过限流。
        String clientIp = resolveClientIp(exchange);
        if (clientIp == null || clientIp.isBlank()) {
            LOGGER.warn("无法识别登录请求的客户端 IP，拒绝继续处理");
            return writeFailure(
                    exchange,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    CommonErrorCode.SERVICE_UNAVAILABLE,
                    "暂时无法识别客户端地址，请稍后重试"
            );
        }

        // Redis 中的令牌桶状态由所有 Gateway 实例共享。
        return Mono.defer(() -> redisRateLimiter.isAllowed(
                        LOGIN_RATE_LIMIT_ID,
                        clientIp
                ))
                .materialize()
                .flatMap(signal -> handleRateLimitSignal(
                        exchange,
                        chain,
                        signal
                ));
    }

    /**
     * 设置本过滤器在 Gateway 全局过滤器链中的执行顺序。
     *
     * @return 过滤器顺序
     */
    @Override
    public int getOrder() {
        // 在路由转发前执行限流，避免超限请求到达认证服务。
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }

    /**
     * 判断当前请求是否为登录接口请求。
     *
     * @param exchange 当前请求和响应交换对象
     * @return 请求方法和路径均匹配时返回 true
     */
    private boolean isLoginRequest(ServerWebExchange exchange) {
        // 限流只作用于精确登录路径的 POST 请求。
        return HttpMethod.POST.equals(exchange.getRequest().getMethod())
                && LOGIN_PATH.equals(
                        exchange.getRequest().getURI().getRawPath()
                );
    }

    /**
     * 从 TCP 连接信息中解析客户端地址。
     *
     * @param exchange 当前请求和响应交换对象
     * @return 客户端 IP；连接地址不存在时返回 null
     */
    private String resolveClientIp(ServerWebExchange exchange) {
        // 不使用未经信任的 X-Forwarded-For 或 Forwarded 请求头。
        InetSocketAddress remoteAddress = exchange.getRequest()
                .getRemoteAddress();
        if (remoteAddress == null) {
            return null;
        }

        // 已解析地址时返回规范 IP 字符串；未解析时使用连接地址主机名。
        if (remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        return remoteAddress.getHostString();
    }

    /**
     * 根据 Redis 令牌桶判定结果继续转发或返回 HTTP 429。
     *
     * @param exchange 当前请求和响应交换对象
     * @param chain Gateway 过滤器链
     * @param rateLimitResponse Redis 限流判定结果
     * @return 当前请求的响应式处理结果
     */
    private Mono<Void> handleRateLimitResponse(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            RateLimiter.Response rateLimitResponse
    ) {
        // 将令牌桶状态头返回给客户端，便于调用方和调试工具观察。
        copyRateLimitHeaders(
                exchange.getResponse(),
                rateLimitResponse.getHeaders()
        );

        // 令牌充足时继续执行 Gateway 路由链。
        if (rateLimitResponse.isAllowed()) {
            return chain.filter(exchange);
        }

        // 令牌不足时返回 HTTP 429 和项目统一错误结构。
        return writeFailure(
                exchange,
                HttpStatus.TOO_MANY_REQUESTS,
                CommonErrorCode.TOO_MANY_REQUESTS,
                "登录请求过于频繁，请稍后再试"
        );
    }

    /**
     * 在进入下游 Gateway 链之前处理限流结果、异常和空结果。
     *
     * @param exchange 当前请求和响应交换对象
     * @param chain Gateway 过滤器链
     * @param signal Redis 限流结果信号
     * @return 当前请求的响应式处理结果
     */
    private Mono<Void> handleRateLimitSignal(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            Signal<RateLimiter.Response> signal
    ) {
        // Redis 限流检查异常时拒绝请求，不允许绕过保护继续登录。
        if (signal.isOnError()) {
            return handleRateLimiterFailure(
                    exchange,
                    signal.getThrowable()
            );
        }

        // 限流器没有返回判定结果时按不可用处理。
        if (!signal.hasValue()) {
            LOGGER.warn("Gateway 登录限流器未返回判定结果，拒绝继续处理");
            return writeFailure(
                    exchange,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    CommonErrorCode.SERVICE_UNAVAILABLE,
                    "登录服务暂不可用，请稍后重试"
            );
        }

        // 只有限流检查成功后才进入路由链，避免改写下游服务错误。
        return handleRateLimitResponse(
                exchange,
                chain,
                signal.get()
        );
    }

    /**
     * 将 Redis 限流器返回的响应头复制到 Gateway 响应。
     *
     * @param response Gateway HTTP 响应
     * @param headers Redis 限流器响应头
     */
    private void copyRateLimitHeaders(
            ServerHttpResponse response,
            Map<String, String> headers
    ) {
        // 保留 Spring Cloud Gateway 提供的限流状态信息。
        for (Map.Entry<String, String> header : headers.entrySet()) {
            response.getHeaders().set(header.getKey(), header.getValue());
        }
    }

    /**
     * 记录 Redis 限流异常并以 HTTP 503 拒绝登录请求。
     *
     * @param exchange 当前请求和响应交换对象
     * @param exception Redis 限流异常
     * @return 统一服务不可用响应
     */
    private Mono<Void> handleRateLimiterFailure(
            ServerWebExchange exchange,
            Throwable exception
    ) {
        // 日志不记录登录请求体或密码等敏感信息。
        LOGGER.warn("Gateway 登录限流检查失败，按 fail-closed 策略拒绝请求", exception);
        return writeFailure(
                exchange,
                HttpStatus.SERVICE_UNAVAILABLE,
                CommonErrorCode.SERVICE_UNAVAILABLE,
                "登录服务暂不可用，请稍后重试"
        );
    }

    /**
     * 写入项目统一格式的失败响应。
     *
     * @param exchange 当前请求和响应交换对象
     * @param status HTTP 状态码
     * @param errorCode 项目错误码
     * @param message 面向调用方的提示信息
     * @return 响应写入完成信号
     */
    private Mono<Void> writeFailure(
            ServerWebExchange exchange,
            HttpStatus status,
            ErrorCode errorCode,
            String message
    ) {
        // 设置 HTTP 状态和 UTF-8 JSON 响应头。
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(
                new MediaType(
                        MediaType.APPLICATION_JSON.getType(),
                        MediaType.APPLICATION_JSON.getSubtype(),
                        StandardCharsets.UTF_8
                )
        );

        try {
            // 失败响应遵循项目统一 ApiResponse 格式。
            byte[] responseBytes = objectMapper.writeValueAsBytes(
                    ApiResponse.failure(errorCode, message)
            );
            return response.writeWith(Mono.just(
                    response.bufferFactory().wrap(responseBytes)
            ));
        } catch (JsonProcessingException exception) {
            // 序列化失败不能静默吞掉，交由 WebFlux 错误处理机制处理。
            return Mono.error(exception);
        }
    }
}
