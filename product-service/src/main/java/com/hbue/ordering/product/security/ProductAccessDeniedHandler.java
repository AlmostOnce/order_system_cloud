package com.hbue.ordering.product.security;

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
 * 商品服务无权请求统一返回 403 响应。
 *
 * @author order-system
 * @date 2026-09-28
 */
@Component
public class ProductAccessDeniedHandler implements AccessDeniedHandler {

    /** JSON 序列化工具。 */
    private final ObjectMapper objectMapper;

    /**
     * 创建无权访问处理器。
     *
     * @param objectMapper JSON 序列化工具
     */
    public ProductAccessDeniedHandler(ObjectMapper objectMapper) {
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
