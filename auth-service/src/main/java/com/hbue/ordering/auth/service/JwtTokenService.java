package com.hbue.ordering.auth.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.hbue.ordering.auth.service.JwtTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * JWT Token 服务。
 *
 * <p>负责使用 RSA 私钥签发访问令牌。</p>
 *
 * @author order-system
 * @date 2026-09-21
 */
@Service
public class JwtTokenService {

    /**
     * 访问令牌有效期，单位为秒。
     */
    private static final long ACCESS_TOKEN_TTL_SECONDS = 1800L;

    /**
     * JWT 编码器。
     */
    private final JwtEncoder jwtEncoder;

    /**
     * JWT 签发方。
     */
    private final String issuer;

    /**
     * RSA 密钥编号。
     */
    private final String keyId;

    /**
     * 创建 JWT Token 服务。
     *
     * @param jwtEncoder JWT 编码器
     * @param issuer JWT 签发方
     * @param keyId RSA 密钥编号
     */
    public JwtTokenService(
            JwtEncoder jwtEncoder,
            @Value("${security.jwt.issuer}") String issuer,
            @Value("${security.jwt.key-id}") String keyId
    ) {
        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.keyId = keyId;
    }

    /**
     * 创建访问 Token。
     *
     * @param userId 用户 ID
     * @param username 用户名
     * @param roleCode 角色编码
     * @return JWT 访问 Token
     */
    public String createAccessToken(
            Long userId,
            String username,
            String roleCode
    ) {
        // 获取当前时间。
        Instant issuedAt = Instant.now();

        // 创建 JWT 请求头。
        JwsHeader headers = JwsHeader
                .with(SignatureAlgorithm.RS256)
                .keyId(keyId)
                .build();

        // 创建 JWT 声明。
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("roles", List.of(roleCode))
                .claim("token_type", "access")
                .issuedAt(issuedAt)
                .expiresAt(
                        issuedAt.plusSeconds(ACCESS_TOKEN_TTL_SECONDS)
                )
                .build();

        // 使用 RSA 私钥签发 JWT。
        return jwtEncoder.encode(
                JwtEncoderParameters.from(headers, claims)
        ).getTokenValue();
    }

    /**
     * 获取访问 Token 有效期。
     *
     * @return Token 有效期，单位为秒
     */
    public long getAccessTokenTtlSeconds() {
        // 返回访问 Token 的有效期。
        return ACCESS_TOKEN_TTL_SECONDS;
    }
}
