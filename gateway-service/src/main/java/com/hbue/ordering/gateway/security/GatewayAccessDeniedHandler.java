package com.hbue.ordering.gateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Gateway 无权限处理器。
 *
 * <p>当用户已经登录，
 * 但是当前角色没有访问目标接口的权限时，
 * 统一返回项目定义的 403 响应。</p>
 *
 * @author order-system
 * @date 2026-09-22
 */
@Component
public class GatewayAccessDeniedHandler
        implements ServerAccessDeniedHandler {

    /**
     * JSON 序列化工具。
     */
    private final ObjectMapper objectMapper;

    /**
     * 创建 Gateway 无权限处理器。
     *
     * @param objectMapper JSON 序列化工具
     */
    public GatewayAccessDeniedHandler(
            ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper;
    }

    /**
     * 处理无权限请求。
     *
     * @param exchange WebFlux 请求交换对象
     * @param exception 无权限异常
     * @return 响应完成信号
     */
    @Override
    public Mono<Void> handle(
            ServerWebExchange exchange,
            AccessDeniedException exception
    ) {
        // 获取响应对象。
        ServerHttpResponse response =
                exchange.getResponse();

        // 设置 HTTP 403 状态码。
        response.setStatusCode(HttpStatus.FORBIDDEN);

        // 设置响应内容类型和字符集。
        response.getHeaders().setContentType(
                new MediaType(
                        MediaType.APPLICATION_JSON.getType(),
                        MediaType.APPLICATION_JSON.getSubtype(),
                        StandardCharsets.UTF_8
                )
        );

        // 构造统一无权限响应。
        ApiResponse<Void> apiResponse =
                ApiResponse.failure(
                        CommonErrorCode.FORBIDDEN,
                        "没有访问权限"
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
