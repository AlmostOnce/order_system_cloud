package com.hbue.ordering.cart.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 购物车安全过滤链的统一错误响应处理器。
 *
 * <p>同时处理未认证和权限不足；安全异常发生在 Controller 之前，不能依赖
 * MVC 全局异常处理器。仅返回统一错误码，不暴露 JWT 或内部校验异常。</p>
 *
 * @author order-system
 * @date 2026-10-17
 */
@Component
public class CartSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    /** 应用共享的 JSON 序列化组件。 */
    private final ObjectMapper objectMapper;

    /**
     * 创建安全错误响应处理器。
     *
     * @param objectMapper 应用共享的 JSON 序列化组件
     */
    public CartSecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 为缺少令牌或无效令牌返回 HTTP 401。
     *
     * @param request 当前请求
     * @param response 待写入响应
     * @param exception 认证失败原因，仅用于过滤链分派
     * @throws IOException 响应写入失败时抛出
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        // Bearer 令牌校验失败与未提供令牌统一使用项目的未认证响应。
        writeFailure(response, HttpServletResponse.SC_UNAUTHORIZED, CommonErrorCode.UNAUTHORIZED);
    }

    /**
     * 为已认证但缺少顾客角色的请求返回 HTTP 403。
     *
     * @param request 当前请求
     * @param response 待写入响应
     * @param exception 权限不足原因
     * @throws IOException 响应写入失败时抛出
     */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        // 身份有效但权限不足属于授权失败，与 401 分开处理。
        writeFailure(response, HttpServletResponse.SC_FORBIDDEN, CommonErrorCode.FORBIDDEN);
    }

    /**
     * 写入 UTF-8 JSON 格式的安全错误响应。
     *
     * @param response 待写入响应
     * @param status HTTP 状态码
     * @param errorCode 对应的项目错误码
     * @throws IOException JSON 写入失败时抛出
     */
    private void writeFailure(HttpServletResponse response, int status,
                              CommonErrorCode errorCode) throws IOException {
        // HTTP 状态和业务错误码同时设置，便于客户端按同一规则处理安全失败。
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.failure(errorCode));
    }
}
