package com.hbue.ordering.statistics.security;

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

/** 把统计服务的未登录与角色拒绝响应保持为项目统一 ApiResponse JSON。 */
@Component
public class StatisticsSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
    private final ObjectMapper json;

    /** 注入项目统一 JSON 编码器，避免安全错误走 Spring 默认 HTML 响应。 */
    public StatisticsSecurityErrorHandler(ObjectMapper json) {
        this.json = json;
    }

    /** 未通过 JWT 验证时返回统一401，不把令牌校验细节写入响应。 */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException {
        write(response, HttpServletResponse.SC_UNAUTHORIZED, CommonErrorCode.UNAUTHORIZED);
    }

    /** JWT 有效但不是 CUSTOMER 时返回统一403。 */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception) throws IOException {
        write(response, HttpServletResponse.SC_FORBIDDEN, CommonErrorCode.FORBIDDEN);
    }

    /** 以 UTF-8 写出不含内部异常信息的统一错误包络。 */
    private void write(HttpServletResponse response, int status, CommonErrorCode code) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        json.writeValue(response.getWriter(), ApiResponse.failure(code));
    }
}
