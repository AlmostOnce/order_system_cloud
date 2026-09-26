package com.hbue.ordering.order.config;

import com.hbue.ordering.common.redis.RedisOperationsService;
import com.hbue.ordering.order.security.OrderAccessDeniedHandler;
import com.hbue.ordering.order.security.OrderAuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.Objects;

/**
 * 订单服务安全配置。
 *
 * <p>订单服务作为 JWT 资源服务，
 * 负责校验 Gateway 转发过来的访问令牌。</p>
 *
 * <p>同时统一处理未认证和无权限请求，
 * 保证返回项目统一响应格式。</p>
 *
 * @author order-system
 * @date 2026-09-22
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class OrderSecurityConfig {

    /**
     * 未认证请求处理器。
     */
    private final OrderAuthenticationEntryPoint authenticationEntryPoint;

    /**
     * 无权限请求处理器。
     */
    private final OrderAccessDeniedHandler accessDeniedHandler;

    /**
     * 创建订单服务安全配置。
     *
     * @param authenticationEntryPoint 未认证请求处理器
     * @param accessDeniedHandler 无权限请求处理器
     */
    public OrderSecurityConfig(
            OrderAuthenticationEntryPoint authenticationEntryPoint,
            OrderAccessDeniedHandler accessDeniedHandler
    ) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    /**
     * 配置订单服务安全过滤链。
     *
     * @param http HTTP 安全配置
     * @return 安全过滤链
     * @throws Exception 安全配置异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {
        http
                // REST 服务不使用 CSRF。
                .csrf(csrf -> csrf.disable())

                // JWT 是无状态认证，不使用 HTTP Session。
                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                // 关闭默认表单登录。
                .formLogin(formLogin -> formLogin.disable())

                // 关闭默认 HTTP Basic 认证。
                .httpBasic(httpBasic -> httpBasic.disable())

                // 配置统一的认证异常和权限异常处理器。
                .exceptionHandling(exception -> exception
                        // 没有登录或 Token 无效时返回 401。
                        .authenticationEntryPoint(
                                authenticationEntryPoint
                        )

                        // 已登录但权限不足时返回 403。
                        .accessDeniedHandler(
                                accessDeniedHandler
                        )
                )

                // 配置接口访问权限。
                .authorizeHttpRequests(authorize -> authorize
                        // 健康检查允许匿名访问。
                        .requestMatchers("/actuator/health")
                        .permitAll()

                        // 其他接口必须携带有效 JWT。
                        .anyRequest()
                        .authenticated()
                )

                // 使用 JWT 校验 Bearer Token。
                .oauth2ResourceServer(
                        oauth2 -> oauth2.jwt(
                                jwt -> jwt.jwtAuthenticationConverter(
                                        jwtAuthenticationConverter()
                                )
                        )
                );

        // 返回安全过滤链。
        return http.build();
    }

    /**
     * 配置 JWT 角色转换器。
     *
     * @return JWT 角色转换器
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        // 创建 JWT 权限转换器。
        JwtGrantedAuthoritiesConverter authoritiesConverter =
                new JwtGrantedAuthoritiesConverter();

        // 从 JWT 的 roles 声明中读取角色。
        authoritiesConverter.setAuthoritiesClaimName("roles");

        // 将 CUSTOMER 转换为 ROLE_CUSTOMER。
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        // 创建 JWT 认证转换器。
        JwtAuthenticationConverter authenticationConverter =
                new JwtAuthenticationConverter();

        // 设置角色转换规则。
        authenticationConverter.setJwtGrantedAuthoritiesConverter(
                authoritiesConverter
        );

        // 返回 JWT 认证转换器。
        return authenticationConverter;
    }

    /**
     * 创建 JWT 解码器。
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
            @Value("${security.jwt.issuer}")
            String issuer,
            RedisOperationsService redisOperationsService,
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

            // 使用 RSA 公钥创建 JWT 解码器。
            NimbusJwtDecoder decoder =
                    NimbusJwtDecoder
                            .withPublicKey(publicKey)
                            .build();

            // 校验签发方、过期时间和生效时间。
            decoder.setJwtValidator(
                    JwtValidators.createDefaultWithIssuer(issuer)
            );

            // 返回带 Redis 黑名单校验的 JWT 解码器。
            return token -> decodeAndCheckBlacklist(
                    decoder,
                    token,
                    redisOperationsService,
                    blacklistKeyPrefix
            );
        } catch (IOException exception) {
            // 公钥读取失败时，订单服务必须启动失败。
            throw new IllegalStateException(
                    "读取 JWT RSA 公钥失败",
                    exception
            );
        }
    }

    /**
     * 解码 JWT 并检查访问令牌黑名单。
     *
     * @param decoder RSA JWT 解码器
     * @param token 原始 JWT
     * @param redisOperationsService Redis 基础操作组件
     * @param blacklistKeyPrefix 黑名单 Key 前缀
     * @return 已通过校验的 JWT
     */
    private Jwt decodeAndCheckBlacklist(
            JwtDecoder decoder,
            String token,
            RedisOperationsService redisOperationsService,
            String blacklistKeyPrefix
    ) {
        // 先验证 JWT 签名、签发方和过期时间。
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
