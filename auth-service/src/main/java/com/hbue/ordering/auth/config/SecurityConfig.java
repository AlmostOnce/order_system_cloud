package com.hbue.ordering.auth.config;

import com.hbue.ordering.common.redis.RedisOperationsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.Objects;

/**
 * 认证服务安全配置。
 *
 * @author order-system
 * @date 2026-09-20
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 配置认证服务安全过滤链。
     *
     * @param http HTTP 安全配置
     * @param jwtDecoder JWT 解码器
     * @return 安全过滤链
     * @throws Exception 安全配置异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder
    ) throws Exception {
        http
                // 当前服务使用 REST 接口，关闭 CSRF。
                .csrf(AbstractHttpConfigurer::disable)

                // 关闭默认表单登录。
                .formLogin(AbstractHttpConfigurer::disable)

                // 关闭默认 HTTP Basic 认证。
                .httpBasic(AbstractHttpConfigurer::disable)

                // 使用无状态 JWT 认证，不创建 HTTP Session。
                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                // 配置接口访问权限。
                .authorizeHttpRequests(authorize -> authorize
                        // 登录接口允许匿名访问。
                        .requestMatchers(
                                "/auth/login",
                                "/auth/register",
                                "/auth/refresh"
                        ).permitAll()

                        // 注销必须携带有效的 Access Token。
                        .requestMatchers("/auth/logout").authenticated()

                        // 健康检查允许匿名访问。
                        .requestMatchers("/actuator/health").permitAll()

                        // 其他接口暂时要求认证。
                        .anyRequest().authenticated()
                )

                // 由资源服务器校验 Bearer JWT 和 Redis 黑名单。
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(
                        jwt -> jwt.decoder(jwtDecoder)
                ));

        // 返回安全过滤链。
        return http.build();
    }

    /**
     * 创建校验签名、签发方和 Redis 黑名单的 JWT 解码器。
     *
     * @param publicKeyResource RSA 公钥资源
     * @param issuer JWT 签发方
     * @param redisOperationsService Redis 基础操作组件
     * @param blacklistKeyPrefix Access Token 黑名单 Key 前缀
     * @return JWT 解码器
     */
    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${security.jwt.public-key-location}")
            Resource publicKeyResource,
            @Value("${security.jwt.issuer}") String issuer,
            RedisOperationsService redisOperationsService,
            @Value("${security.jwt.blacklist-key-prefix:auth:access-token:blacklist:}")
            String blacklistKeyPrefix
    ) {
        try (
                InputStream inputStream =
                        publicKeyResource.getInputStream()
        ) {
            // 读取 X.509 格式的 RSA 公钥。
            RSAPublicKey publicKey = Objects.requireNonNull(
                    RsaKeyConverters.x509().convert(inputStream),
                    "RSA 公钥不能为空"
            );

            // 创建基础解码器，验证签名、签发方和时间声明。
            NimbusJwtDecoder decoder = NimbusJwtDecoder
                    .withPublicKey(publicKey)
                    .build();
            decoder.setJwtValidator(
                    JwtValidators.createDefaultWithIssuer(issuer)
            );

            // 在标准 JWT 校验后检查 jti 黑名单。
            return token -> decodeAndCheckBlacklist(
                    decoder,
                    token,
                    redisOperationsService,
                    blacklistKeyPrefix
            );
        } catch (IOException exception) {
            // 公钥读取失败时，认证服务必须启动失败。
            throw new IllegalStateException(
                    "读取 JWT RSA 公钥失败",
                    exception
            );
        }
    }

    /**
     * 解码 JWT 并拒绝黑名单中的访问令牌。
     *
     * @param decoder RSA JWT 解码器
     * @param token 原始 JWT
     * @param redisOperationsService Redis 基础操作组件
     * @param blacklistKeyPrefix 黑名单 Key 前缀
     * @return 已校验的 JWT
     */
    private Jwt decodeAndCheckBlacklist(
            JwtDecoder decoder,
            String token,
            RedisOperationsService redisOperationsService,
            String blacklistKeyPrefix
    ) {
        // 先验证 JWT 签名和标准声明。
        Jwt jwt = decoder.decode(token);

        // jti 是黑名单查找所必需的令牌唯一标识。
        if (jwt.getId() == null || jwt.getId().isBlank()) {
            throw invalidToken("访问令牌缺少 jti 声明");
        }

        try {
            // Redis 无法确认令牌状态时按拒绝访问处理，避免故障时放行。
            Boolean blacklisted = redisOperationsService.hasKey(
                    blacklistKeyPrefix + jwt.getId()
            );
            if (!Boolean.FALSE.equals(blacklisted)) {
                throw invalidToken("访问令牌已注销或状态无法确认");
            }
        } catch (JwtException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            // 黑名单服务不可用时拒绝访问，确保注销语义不被绕过。
            throw new JwtException(
                    "无法校验访问令牌黑名单",
                    exception
            );
        }

        // 返回通过签名、声明和黑名单校验的 JWT。
        return jwt;
    }

    /**
     * 创建统一的无效访问令牌异常。
     *
     * @param description 失败原因
     * @return JWT 校验异常
     */
    private JwtValidationException invalidToken(String description) {
        // 使用 OAuth2 标准错误码，让资源服务器按 401 处理。
        return new JwtValidationException(
                description,
                List.of(new OAuth2Error(
                        "invalid_token",
                        description,
                        null
                ))
        );
    }
}
