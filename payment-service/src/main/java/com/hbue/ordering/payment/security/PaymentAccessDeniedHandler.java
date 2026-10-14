package com.hbue.ordering.payment.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 支付服务已认证但无权访问时的统一 403 响应边界。
 *
 * <p>将 Spring Security 权限拒绝转换为项目统一 JSON 响应，不暴露安全过滤器内部细节；
 * 未认证请求仍由 {@link PaymentAuthenticationEntryPoint} 返回 401。</p>
 *
 * @author order-system
 * @date 2026-10-12
 */
@Component
public class PaymentAccessDeniedHandler implements AccessDeniedHandler {

    /** JSON 序列化工具。 */
    private final ObjectMapper objectMapper;

    /**
     * 创建无权访问处理器。
     *
     * @param objectMapper JSON 序列化工具
     */
    public PaymentAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 写入统一格式的无权访问响应。
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param exception 权限异常
     * @throws IOException 响应写入异常
     */
    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception
    ) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(
                response.getWriter(),
                ApiResponse.failure(CommonErrorCode.FORBIDDEN)
        );
    }
}
