package com.hbue.ordering.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Objects;

/**
 * JWT 密钥配置。
 *
 * <p>认证服务使用 RSA 私钥签发 JWT，
 * Gateway 和业务服务使用 RSA 公钥校验 JWT。</p>
 *
 * <p>私钥通过外部文件注入，禁止写入代码和 Git 仓库。</p>
 *
 * @author order-system
 * @date 2026-09-20
 */
@Configuration
public class JwtKeyConfig {

    private final Resource privateKeyResource;
    private final Resource publicKeyResource;
    private final String keyId;

    /**
     * 创建 JWT 密钥配置。
     *
     * @param privateKeyResource RSA 私钥资源
     * @param publicKeyResource RSA 公钥资源
     * @param keyId 密钥编号
     */
    public JwtKeyConfig(
            @Value("${security.jwt.private-key-location}")
            Resource privateKeyResource,
            @Value("${security.jwt.public-key-location}")
            Resource publicKeyResource,
            @Value("${security.jwt.key-id}")
            String keyId
    ) {
        this.privateKeyResource = privateKeyResource;
        this.publicKeyResource = publicKeyResource;
        this.keyId = keyId;
    }

    /**
     * 加载 RSA 密钥。
     *
     * @return RSA 密钥
     */
    @Bean
    public RSAKey jwtRsaKey() {
        try (
                InputStream privateKeyInputStream =
                        privateKeyResource.getInputStream();
                InputStream publicKeyInputStream =
                        publicKeyResource.getInputStream()
        ) {
            // 读取 PKCS#8 格式的 RSA 私钥。
            RSAPrivateKey privateKey =
                    Objects.requireNonNull(
                            RsaKeyConverters.pkcs8()
                                    .convert(privateKeyInputStream),
                            "RSA 私钥不能为空"
                    );

            // 读取 X.509 格式的 RSA 公钥。
            RSAPublicKey publicKey =
                    Objects.requireNonNull(
                            RsaKeyConverters.x509()
                                    .convert(publicKeyInputStream),
                            "RSA 公钥不能为空"
                    );

            // 组装供 JWT 使用的 RSA 密钥。
            return new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyID(keyId)
                    .build();
        } catch (IOException exception) {
            // 密钥文件读取失败时，认证服务必须启动失败。
            throw new IllegalStateException(
                    "读取 JWT RSA 密钥失败",
                    exception
            );
        }
    }

    /**
     * 创建 JWT 编码器。
     *
     * @param rsaKey RSA 密钥
     * @return JWT 编码器
     */
    @Bean
    public JwtEncoder jwtEncoder(RSAKey rsaKey) {
        // 创建 JWK 数据源。
        JWKSource<SecurityContext> jwkSource =
                (jwkSelector, securityContext) ->
                        jwkSelector.select(new JWKSet(rsaKey));

        // 使用 RSA 私钥签发 JWT。
        return new NimbusJwtEncoder(jwkSource);
    }
}