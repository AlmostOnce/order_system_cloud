package com.hbue.ordering.gateway.config;

import com.hbue.ordering.common.redis.ReactiveRedisOperationsService;
import com.hbue.ordering.gateway.security.GatewayAccessDeniedHandler;
import com.hbue.ordering.gateway.security.GatewayAuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPublicKey;
import java.util.Objects;
import java.util.List;

import reactor.core.publisher.Mono;

/**
 * Gateway 安全配置。
 *
 * <p>Gateway 作为 JWT 资源服务，
 * 负责校验客户端请求中的 Bearer Token。</p>
 *
 * <p>Gateway 只使用 RSA 公钥校验 JWT，
 * 不读取也不保存 JWT 私钥。</p>
 *
 * @author order-system
 * @date 2026-09-22
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    /**
     * Gateway 未认证处理器。
     */
    private final GatewayAuthenticationEntryPoint authenticationEntryPoint;

    /**
     * Gateway 无权限处理器。
     */
    private final GatewayAccessDeniedHandler accessDeniedHandler;

    /**
     * 创建 Gateway 安全配置。
     *
     * @param authenticationEntryPoint 未认证处理器
     * @param accessDeniedHandler 无权限处理器
     */
    public GatewaySecurityConfig(
            GatewayAuthenticationEntryPoint authenticationEntryPoint,
            GatewayAccessDeniedHandler accessDeniedHandler
    ) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    /**
     * 配置 Gateway 响应式安全过滤链。
     *
     * @param http 响应式 HTTP 安全配置
     * @return 响应式安全过滤链
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http
    ) {
        http
                // Gateway 使用 Token 认证，关闭 CSRF。
                .csrf(ServerHttpSecurity.CsrfSpec::disable)

                // 关闭默认表单登录。
                .formLogin(
                        ServerHttpSecurity.FormLoginSpec::disable
                )

                // 关闭默认 HTTP Basic 认证。
                .httpBasic(
                        ServerHttpSecurity.HttpBasicSpec::disable
                )

                // 配置统一认证异常和权限异常处理。
                .exceptionHandling(exception -> exception
                        // 没有 Token 或 Token 无效时返回 401。
                        .authenticationEntryPoint(
                                authenticationEntryPoint
                        )

                        // 已登录但权限不足时返回 403。
                        .accessDeniedHandler(
                                accessDeniedHandler
                        )
                )

                // 配置接口访问权限。
                .authorizeExchange(exchange -> exchange
                        // 登录接口允许匿名访问。
                        .pathMatchers("/api/auth/login")
                        .permitAll()

                        // 注册接口允许匿名访问。
                        .pathMatchers("/api/auth/register")
                        .permitAll()

                        // 刷新 Token 接口允许匿名访问。
                        .pathMatchers("/api/auth/refresh")
                        .permitAll()

                        // 健康检查允许匿名访问。
                        .pathMatchers("/actuator/health")
                        .permitAll()

                        // 其他请求必须携带有效 JWT。
                        .anyExchange()
                        .authenticated()
                )

                // 使用 JWT 校验 Bearer Token。
                .oauth2ResourceServer(
                        oauth2 -> oauth2.jwt(
                                Customizer.withDefaults()
                        )
                );

        // 返回响应式安全过滤链。
        return http.build();
    }

    /**
     * 创建 JWT 解码器。
     *
     * @param publicKeyResource RSA 公钥资源
     * @param issuer JWT 签发方
     * @param redisOperationsService Redis 响应式基础操作组件
     * @param blacklistKeyPrefix Access Token 黑名单 Key 前缀
     * @return JWT 解码器
     */
    @Bean
    public ReactiveJwtDecoder jwtDecoder(
            @Value("${security.jwt.public-key-location}")
            Resource publicKeyResource,
            @Value("${security.jwt.issuer}")
            String issuer,
            ReactiveRedisOperationsService redisOperationsService,
            @Value("${security.jwt.blacklist-key-prefix:auth:access-token:blacklist:}")
            String blacklistKeyPrefix
    ) {
        try (
                InputStream inputStream =
                        publicKeyResource.getInputStream()
        ) {
            // 读取 X.509 格式的 RSA 公钥。
            RSAPublicKey publicKey =
                    Objects.requireNonNull(
                            RsaKeyConverters.x509()
                                    .convert(inputStream),
                            "RSA 公钥不能为空"
                    );

            // 使用 RSA 公钥创建响应式 JWT 解码器。
            NimbusReactiveJwtDecoder decoder =
                    NimbusReactiveJwtDecoder
                            .withPublicKey(publicKey)
                            .build();

            // 校验 JWT 的签发方、过期时间和生效时间。
            decoder.setJwtValidator(
                    JwtValidators.createDefaultWithIssuer(issuer)
            );

            // 在响应式流程中查询黑名单，避免阻塞 Gateway 请求线程。
            return token -> decodeAndCheckBlacklist(
                    decoder,
                    token,
                    redisOperationsService,
                    blacklistKeyPrefix
            );
        } catch (IOException exception) {
            // 公钥读取失败时，Gateway 必须启动失败。
            throw new IllegalStateException(
                    "读取 JWT RSA 公钥失败",
                    exception
            );
        }
    }

    /**
     * 响应式解码 JWT 并检查访问令牌黑名单。
     *
     * @param decoder RSA JWT 解码器
     * @param token 原始 JWT
     * @param redisOperationsService Redis 响应式基础操作组件
     * @param blacklistKeyPrefix 黑名单 Key 前缀
     * @return 通过校验的 JWT，或表示校验失败的错误信号
     */
    private Mono<Jwt> decodeAndCheckBlacklist(
            ReactiveJwtDecoder decoder,
            String token,
            ReactiveRedisOperationsService redisOperationsService,
            String blacklistKeyPrefix
    ) {
        // 先验证 JWT 签名、签发方和过期时间。
        return decoder.decode(token)
                .flatMap(jwt -> {
                    // jti 是黑名单查找所必需的令牌唯一标识。
                    if (jwt.getId() == null || jwt.getId().isBlank()) {
                        return Mono.error(
                                invalidToken("访问令牌缺少 jti 声明")
                        );
                    }

                    // 使用响应式 Redis 查询，避免阻塞 Gateway 事件循环。
                    return redisOperationsService.hasKey(
                                    blacklistKeyPrefix + jwt.getId()
                            )
                            .switchIfEmpty(Mono.error(
                                    new JwtException(
                                            "Redis 未能确认访问令牌状态"
                                    )
                            ))
                            .flatMap(blacklisted -> {
                                // 只有 Redis 明确返回未命中时才允许请求继续。
                                if (!Boolean.FALSE.equals(blacklisted)) {
                                    return Mono.error(invalidToken(
                                            "访问令牌已注销或状态无法确认"
                                    ));
                                }
                                return Mono.just(jwt);
                            });
                })
                .onErrorMap(exception -> {
                    // 将 Redis 故障转换为 JWT 校验失败，按 401 拒绝请求。
                    if (exception instanceof JwtException) {
                        return exception;
                    }
                    return new JwtException(
                            "无法校验访问令牌黑名单",
                            exception
                    );
                });
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
