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
 * <p>当客户端未携带 Token，或 Token 无效、过期、已注销时，统一返回项目定义的 401 响应；
 * 已认证但角色不足的请求由 {@link OrderAccessDeniedHandler} 负责返回 403。</p>
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
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        response.setContentType(
                MediaType.APPLICATION_JSON_VALUE
        );

        response.setCharacterEncoding(
                StandardCharsets.UTF_8.name()
        );

        // 不将底层 Token 校验细节返回给调用方，避免泄露认证实现信息。
        ApiResponse<Void> apiResponse =
                ApiResponse.failure(
                        CommonErrorCode.UNAUTHORIZED,
                        "未登录或登录已过期"
                );

        objectMapper.writeValue(
                response.getWriter(),
                apiResponse
        );
    }
}
