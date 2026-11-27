package com.hbue.ordering.recommendation.security;

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
 * 推荐服务的登录及角色拒绝响应保持项目统一 JSON，不返回验签或内部身份细节。
 */
@Component
public class RecommendationSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
    private final ObjectMapper json;

    /**
     * 注入 Jackson，以项目 ApiResponse 结构写安全错误。
     */
    public RecommendationSecurityErrorHandler(ObjectMapper json) {
        this.json = json;
    }

    /**
     * 未登录或令牌不可确认时统一返回401。
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        write(response, HttpServletResponse.SC_UNAUTHORIZED, CommonErrorCode.UNAUTHORIZED);
    }

    /**
     * 已登录但不是 CUSTOMER 时返回403。
     */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        write(response, HttpServletResponse.SC_FORBIDDEN, CommonErrorCode.FORBIDDEN);
    }

    /**
     * 不把底层异常或令牌状态详情泄露给客户端。
     */
    private void write(HttpServletResponse response, int status, CommonErrorCode code) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        json.writeValue(response.getWriter(), ApiResponse.failure(code));
    }
}
