package com.hbue.ordering.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

/**
 * JWT Token 服务测试。
 *
 * @author order-system
 * @date 2026-09-25
 */
class JwtTokenServiceTest {

    /**
     * 验证签发的访问 Token 包含可用于黑名单校验的唯一 ID。
     */
    @Test
    void shouldIncludeUniqueTokenIdInAccessTokenClaims() {
        JwtEncoder jwtEncoder = mock(JwtEncoder.class);
        Instant now = Instant.now();
        when(jwtEncoder.encode(any(JwtEncoderParameters.class)))
                .thenReturn(Jwt.withTokenValue("access-token")
                        .header("alg", "RS256")
                        .subject("5")
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(1800L))
                        .build());

        JwtTokenService jwtTokenService = new JwtTokenService(
                jwtEncoder,
                "http://localhost:8084",
                "order-system-rsa-key-2026"
        );

        jwtTokenService.createAccessToken(
                5L,
                "测试用户",
                "CUSTOMER"
        );

        ArgumentCaptor<JwtEncoderParameters> parametersCaptor =
                ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(jwtEncoder).encode(parametersCaptor.capture());

        assertNotNull(parametersCaptor.getValue().getClaims().getId());
    }
}
