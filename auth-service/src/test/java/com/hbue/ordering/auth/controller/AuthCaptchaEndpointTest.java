package com.hbue.ordering.auth.controller;

import com.hbue.ordering.auth.config.SecurityConfig;
import com.hbue.ordering.auth.service.AuthService;
import com.hbue.ordering.auth.service.CaptchaService;
import com.hbue.ordering.auth.vo.CaptchaVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 图片验证码接口访问权限测试。
 *
 * <p>验证码接口必须在登录前可访问，因此未携带 Token 也应返回 200；
 * 该用例可防止安全白名单调整时误删 /auth/captcha 导致登录入口不可用。</p>
 *
 * @author order-system
 * @date 2026-10-21
 */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthCaptchaEndpointTest {

    /**
     * MVC 测试入口。
     */
    @Autowired
    private MockMvc mockMvc;

    /**
     * 认证业务服务测试替身。
     */
    @MockitoBean
    private AuthService authService;

    /**
     * 验证码服务测试替身。
     */
    @MockitoBean
    private CaptchaService captchaService;

    /**
     * JWT 解码器测试替身，避免安全配置加载真实密钥。
     */
    @MockitoBean
    private JwtDecoder jwtDecoder;

    /**
     * 验证未携带访问令牌也能获取验证码。
     *
     * @throws Exception MockMvc 执行异常
     */
    @Test
    void shouldAllowAnonymousCaptchaRequest() throws Exception {
        when(captchaService.generate(any())).thenReturn(
                CaptchaVO.builder()
                        .captchaId("captcha-id")
                        .imageBase64("data:image/png;base64,AAA")
                        .expiresIn(300L)
                        .build()
        );

        mockMvc.perform(get("/auth/captcha"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.data.captchaId")
                                .value("captcha-id")
                );
    }
}
