package com.hbue.ordering.user.security;

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
 * 用户服务已认证但权限不足时统一写入 403 响应。
 *
 * <p>该处理器将 Spring Security 的授权拒绝转换为项目统一 JSON 响应，避免
 * 不同安全链路返回不同格式；不向客户端暴露异常堆栈或内部授权细节。</p>
 *
 * @author order-system
 * @date 2026-10-13
 */
@Component
public class UserAccessDeniedHandler implements AccessDeniedHandler {

    /** JSON 序列化工具。 */
    private final ObjectMapper objectMapper;

    /**
     * 创建无权访问处理器。
     *
     * @param objectMapper JSON 序列化工具
     */
    public UserAccessDeniedHandler(ObjectMapper objectMapper) {
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
