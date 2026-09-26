package com.hbue.ordering.order.security;

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
 * 订单服务无权限处理器。
 *
 * <p>当用户已经完成登录，
 * 但是当前角色没有访问目标接口的权限时，
 * 统一返回项目定义的 403 响应。</p>
 *
 * @author order-system
 * @date 2026-09-22
 */
@Component
public class OrderAccessDeniedHandler
        implements AccessDeniedHandler {

    /**
     * JSON 序列化工具。
     */
    private final ObjectMapper objectMapper;

    /**
     * 创建无权限处理器。
     *
     * @param objectMapper JSON 序列化工具
     */
    public OrderAccessDeniedHandler(
            ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper;
    }

    /**
     * 处理无权限请求。
     *
     * @param request HTTP 请求
     * @param response HTTP 响应
     * @param accessDeniedException 无权限异常
     * @throws IOException 响应写入异常
     */
    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        // 设置 HTTP 状态码。
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);

        // 设置 JSON 响应类型。
        response.setContentType(
                MediaType.APPLICATION_JSON_VALUE
        );

        // 设置 UTF-8 编码。
        response.setCharacterEncoding(
                StandardCharsets.UTF_8.name()
        );

        // 构造统一无权限响应。
        ApiResponse<Void> apiResponse =
                ApiResponse.failure(
                        CommonErrorCode.FORBIDDEN,
                        "没有访问权限"
                );

        // 写出 JSON 响应。
        objectMapper.writeValue(
                response.getWriter(),
                apiResponse
        );
    }
}
