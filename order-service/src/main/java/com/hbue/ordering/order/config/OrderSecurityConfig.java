package com.hbue.ordering.order.config;

import com.hbue.ordering.common.web.security.JwtUserIdentity;
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
 * <p>订单服务作为 JWT 资源服务，负责独立校验访问令牌签名、签发方和有效期，
 * 并通过 Redis 黑名单阻止已注销令牌继续访问；它不信任 Gateway 仅凭转发就完成认证。</p>
 *
 * <p>同时统一处理未认证和无权限请求，保证分别返回 401 与 403 的项目统一响应格式。
 * 内部订单回调路径不要求用户 JWT，但仍由控制器的共享密钥校验；黑名单状态无法确认时拒绝请求。</p>
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

                .formLogin(formLogin -> formLogin.disable())

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

                .authorizeHttpRequests(authorize -> authorize
                        // 健康检查允许匿名访问。
                        .requestMatchers("/actuator/health")
                        .permitAll()

                        // 接口文档路径允许匿名访问，便于本地联调查看。
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        )
                        .permitAll()

                        // 支付服务内部接口无用户 JWT，安全由
                        // InternalTokenVerifier 的共享密钥校验保证。
                        .requestMatchers("/internal/orders/**")
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

        return http.build();
    }

    /**
     * 配置 JWT 角色转换器。
     *
     * @return JWT 角色转换器
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        // 将 JWT 中的 roles 声明映射为 Spring Security 识别的 ROLE_* 权限。
        JwtGrantedAuthoritiesConverter authoritiesConverter =
                new JwtGrantedAuthoritiesConverter();

        authoritiesConverter.setAuthoritiesClaimName("roles");

        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter authenticationConverter =
                new JwtAuthenticationConverter();

        authenticationConverter.setJwtGrantedAuthoritiesConverter(
                authoritiesConverter
        );

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
     * @throws IllegalStateException 读取 RSA 公钥资源发生 I/O 错误时抛出，避免服务以无验证状态启动
     */
    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${security.jwt.public-key-location}")
            Resource publicKeyResource,
            @Value("${security.jwt.issuer}")
            String issuer,
            RedisOperationsService redisOperationsService,
            @Value("${security.jwt.blacklist-key-prefix:auth:access-token:blacklist:}")
            String blacklistKeyPrefix,
            JwtUserIdentity userIdentity
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
            return token -> userIdentity.toInternalPrincipal(decodeAndCheckBlacklist(
                    decoder,
                    token,
                    redisOperationsService,
                    blacklistKeyPrefix
            ));
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
     * @throws JwtException JWT 无效、已注销或黑名单状态无法确认时抛出
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
