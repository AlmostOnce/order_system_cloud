package com.hbue.ordering.order.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 订单服务未认证处理器。
 *
 * <p>当客户端没有携带 Token，
 * 或者 Token 无效、过期时，
 * 统一返回项目定义的 401 响应。</p>
 *
 * @author order-system
 * @date 2026-09-22
 */
@Component
public class OrderAuthenticationEntryPoint
        implements AuthenticationEntryPoint {

    /**
     * JSON 序列化工具。
     */
    private final ObjectMapper objectMapper;

    /**
     * 创建未认证处理器。
     *
     * @param objectMapper JSON 序列化工具
     */
    public OrderAuthenticationEntryPoint(
            ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper;
    }

    /**
     * 处理未认证请求。
     *
     * @param request HTTP 请求
     * @param response HTTP 响应
     * @param exception 认证异常
     * @throws IOException 响应写入异常
     */
    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        // 设置 HTTP 状态码。
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        // 设置 JSON 响应类型。
        response.setContentType(
                MediaType.APPLICATION_JSON_VALUE
        );

        // 设置 UTF-8 编码。
        response.setCharacterEncoding(
                StandardCharsets.UTF_8.name()
        );

        // 构造统一未认证响应。
        ApiResponse<Void> apiResponse =
                ApiResponse.failure(
                        CommonErrorCode.UNAUTHORIZED,
                        "未登录或登录已过期"
                );

        // 写出 JSON 响应。
        objectMapper.writeValue(
                response.getWriter(),
                apiResponse
        );
    }
}
