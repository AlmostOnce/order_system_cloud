package com.hbue.ordering.gateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;


import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Gateway 未认证处理器。
 *
 * <p>当客户端没有携带 Token，
 * 或者 Token 无效、过期时，
 * 统一返回项目定义的 401 响应。</p>
 *
 * @author order-system
 * @date 2026-09-22
 */
@Component
public class GatewayAuthenticationEntryPoint
        implements ServerAuthenticationEntryPoint {

    /**
     * JSON 序列化工具。
     */
    private final ObjectMapper objectMapper;

    /**
     * 创建 Gateway 未认证处理器。
     *
     * @param objectMapper JSON 序列化工具
     */
    public GatewayAuthenticationEntryPoint(
            ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper;
    }

    /**
     * 处理未认证请求。
     *
     * @param exchange WebFlux 请求交换对象
     * @param exception 认证异常
     * @return 响应完成信号
     */
    @Override
    public Mono<Void> commence(
            ServerWebExchange exchange,
            AuthenticationException exception
    ) {
        // 获取响应对象。
        ServerHttpResponse response =
                exchange.getResponse();

        // 设置 HTTP 401 状态码。
        response.setStatusCode(
                HttpStatus.UNAUTHORIZED
        );

        // 设置响应内容类型。
        response.getHeaders().setContentType(
                MediaType.APPLICATION_JSON
        );

        // 设置响应字符集。
        response.getHeaders().setContentType(
                new MediaType(
                        MediaType.APPLICATION_JSON.getType(),
                        MediaType.APPLICATION_JSON.getSubtype(),
                        StandardCharsets.UTF_8
                )
        );

        // 构造统一未认证响应。
        ApiResponse<Void> apiResponse =
                ApiResponse.failure(
                        CommonErrorCode.UNAUTHORIZED,
                        "未登录或登录已过期"
                );

        try {
            // 将统一响应对象转换为 JSON 字节数组。
            byte[] responseBytes =
                    objectMapper.writeValueAsBytes(apiResponse);

            // 创建 WebFlux 响应数据缓冲区。
            return response.writeWith(
                    Mono.just(
                            response.bufferFactory()
                                    .wrap(responseBytes)
                    )
            );
        } catch (JsonProcessingException jsonException) {
            // JSON 序列化失败时终止当前响应。
            return Mono.error(jsonException);
        }
    }
}
