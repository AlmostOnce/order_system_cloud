package com.hbue.ordering.auth.controller;

import com.hbue.ordering.auth.config.SecurityConfig;
import com.hbue.ordering.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注销接口身份校验测试。
 *
 * @author order-system
 * @date 2026-09-25
 */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthLogoutAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    /**
     * 验证未携带访问 Token 的注销请求被拒绝。
     *
     * @throws Exception MockMvc 执行异常
     */
    @Test
    void shouldRejectLogoutWithoutBearerToken() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"sample-refresh-token\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(authService);
    }
}
