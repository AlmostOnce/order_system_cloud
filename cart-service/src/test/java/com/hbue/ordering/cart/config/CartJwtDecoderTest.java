package com.hbue.ordering.cart.config;

import com.hbue.ordering.common.redis.RedisOperationsService;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 购物车 JWT 签名、签发方与注销状态验证测试。
 *
 * <p>测试密钥仅在内存生成，通过实际 Nimbus 编解码校验；不读取或修改本机密钥，
 * Redis 使用替身以验证故障拒绝访问的规则。</p>
 *
 * @author order-system
 * @date 2026-10-17
 */
class CartJwtDecoderTest {

    /** 测试专用 RSA 密钥对，多个用例共享以减少生成成本。 */
    private static KeyPair keyPair;

    /** Redis 黑名单查询替身。 */
    private RedisOperationsService redis;

    /** 包含实际 RSA 验签与黑名单规则的被测解码器。 */
    private JwtDecoder decoder;

    /**
     * 生成与真实凭据无关的测试密钥。
     *
     * @throws Exception RSA 密钥无法生成时抛出
     */
    @BeforeAll
    static void createKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
    }

    /**
     * 从内存 PEM 公钥创建与生产环境同规则的 JWT 解码器。
     */
    @BeforeEach
    void setUp() {
        // 资源加载器只返回测试公钥，不访问用户目录。
        String pem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(keyPair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----\n";
        ResourceLoader loader = mock(ResourceLoader.class);
        when(loader.getResource("memory:cart-public-key"))
                .thenReturn(new ByteArrayResource(pem.getBytes(StandardCharsets.UTF_8)));
        redis = mock(RedisOperationsService.class);
        decoder = new CartSecurityConfig().jwtDecoder(
                loader, "memory:cart-public-key", "test-issuer", redis, "auth:access-token:blacklist:",
                mock(com.hbue.ordering.common.web.security.JwtUserIdentity.class, org.mockito.AdditionalAnswers.returnsFirstArg())
        );
    }

    /** 有效签名、正确签发方且不在黑名单的令牌可以访问。 */
    @Test
    void shouldAcceptValidToken() {
        when(redis.hasKey("auth:access-token:blacklist:cart-jti")).thenReturn(false);
        assertEquals("5", decoder.decode(signedToken("test-issuer", "cart-jti")).getSubject());
    }

    /** 已注销的令牌即使签名正确也必须拒绝。 */
    @Test
    void shouldRejectRevokedToken() {
        when(redis.hasKey(anyString())).thenReturn(true);
        assertThrows(JwtException.class, () -> decoder.decode(signedToken("test-issuer", "cart-jti")));
    }

    /** Redis 故障时不能绕过注销状态检查。 */
    @Test
    void shouldRejectRedisFailure() {
        when(redis.hasKey(anyString())).thenThrow(new IllegalStateException("Redis unavailable"));
        assertThrows(JwtException.class, () -> decoder.decode(signedToken("test-issuer", "cart-jti")));
    }

    /** 缺少 jti 时无法定位注销状态，应在查询 Redis 前拒绝。 */
    @Test
    void shouldRejectMissingTokenId() {
        assertThrows(JwtException.class, () -> decoder.decode(signedToken("test-issuer", null)));
        verifyNoInteractions(redis);
    }

    /** 签发方不匹配时应先拒绝令牌，不查询黑名单。 */
    @Test
    void shouldRejectWrongIssuer() {
        assertThrows(JwtException.class, () -> decoder.decode(signedToken("other-issuer", "cart-jti")));
        verifyNoInteractions(redis);
    }

    /**
     * 用内存私钥签发测试 JWT。
     *
     * @param issuer 测试签发方
     * @param tokenId jti，为 null 时生成缺少 jti 的令牌
     * @return 实际 RS256 签名的 JWT 字符串
     */
    private String signedToken(String issuer, String tokenId) {
        RSAKey rsa = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate()).build();
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsa)));
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder().subject("5").issuer(issuer)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300));
        if (tokenId != null) {
            claims.id(tokenId);
        }
        // 使用与认证服务相同的 RS256 算法，让测试实际覆盖公钥验签。
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims.build())).getTokenValue();
    }
}
