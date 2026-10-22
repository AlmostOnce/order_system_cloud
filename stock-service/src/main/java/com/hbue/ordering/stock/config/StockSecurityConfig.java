package com.hbue.ordering.stock.config;

import com.hbue.ordering.common.web.security.JwtUserIdentity;
import com.hbue.ordering.common.redis.RedisOperationsService;
import com.hbue.ordering.stock.security.StockAccessDeniedHandler;
import com.hbue.ordering.stock.security.StockAuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
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
 * 库存服务安全配置，沿用其它业务服务的签名与注销令牌校验规则。
 *
 * <p>库存服务当前只对外暴露 {@code /internal/**} 内部接口：这些路径不在 Gateway
 * 路由白名单内，外部无法直接访问，过滤链放行后由控制器内的共享密钥校验器鉴权。
 * 其余路径仍需有效 JWT，避免后续新增管理接口时被误开放。</p>
 *
 * @author order-system
 * @date 2026-10-19
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class StockSecurityConfig {

    /** 未认证请求处理器。 */
    private final StockAuthenticationEntryPoint authenticationEntryPoint;

    /** 无权限请求处理器。 */
    private final StockAccessDeniedHandler accessDeniedHandler;

    /**
     * 创建库存服务安全配置。
     *
     * @param authenticationEntryPoint 未认证请求处理器
     * @param accessDeniedHandler 无权限请求处理器
     */
    public StockSecurityConfig(
            StockAuthenticationEntryPoint authenticationEntryPoint,
            StockAccessDeniedHandler accessDeniedHandler
    ) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    /**
     * 配置库存服务的无状态 JWT 安全过滤链。
     *
     * @param http Spring Security HTTP 配置
     * @return 安全过滤链
     * @throws Exception 安全配置异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health").permitAll()
                        // 接口文档路径允许匿名访问，便于本地联调查看。
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        // 内部接口不在 Gateway 路由内，改由共享密钥在方法内校验。
                        .requestMatchers("/internal/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    /**
     * 将 JWT roles 声明映射为 Spring Security 角色。
     *
     * @return JWT 认证转换器
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter =
                new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

    /**
     * 创建校验签名、签发方、有效期和 Redis 黑名单的 JWT 解码器。
     *
     * <p>黑名单状态无法从 Redis 确认时按无效令牌处理，避免 Redis 故障期间继续接受已注销令牌。</p>
     *
     * @param resourceLoader Spring 资源加载器
     * @param publicKeyLocation RSA 公钥资源位置
     * @param issuer JWT 签发方
     * @param redisOperationsService Redis 基础操作服务
     * @param blacklistKeyPrefix Access Token 黑名单 Key 前缀
     * @return JWT 解码器
     */
    @Bean
    public JwtDecoder jwtDecoder(
            ResourceLoader resourceLoader,
            @Value("${security.jwt.public-key-location}") String publicKeyLocation,
            @Value("${security.jwt.issuer}") String issuer,
            RedisOperationsService redisOperationsService,
            @Value("${security.jwt.blacklist-key-prefix:auth:access-token:blacklist:}")
            String blacklistKeyPrefix,
            JwtUserIdentity userIdentity
    ) {
        Resource publicKeyResource = resourceLoader.getResource(publicKeyLocation);
        try (InputStream inputStream = publicKeyResource.getInputStream()) {
            RSAPublicKey publicKey = Objects.requireNonNull(
                    RsaKeyConverters.x509().convert(inputStream),
                    "RSA 公钥不能为空"
            );
            NimbusJwtDecoder decoder = NimbusJwtDecoder
                    .withPublicKey(publicKey)
                    .build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
            return token -> userIdentity.toInternalPrincipal(decodeAndCheckBlacklist(
                    decoder,
                    token,
                    redisOperationsService,
                    blacklistKeyPrefix
            ));
        } catch (IOException exception) {
            throw new IllegalStateException("读取 JWT RSA 公钥失败", exception);
        }
    }

    /**
     * 解码 JWT 并检查访问令牌黑名单。
     *
     * @param decoder RSA JWT 解码器
     * @param token 原始 JWT
     * @param redisOperationsService Redis 基础操作服务
     * @param blacklistKeyPrefix 黑名单 Key 前缀
     * @return 已通过校验的 JWT
     */
    private Jwt decodeAndCheckBlacklist(
            JwtDecoder decoder,
            String token,
            RedisOperationsService redisOperationsService,
            String blacklistKeyPrefix
    ) {
        Jwt jwt = decoder.decode(token);
        if (jwt.getId() == null || jwt.getId().isBlank()) {
            throw invalidToken("访问令牌缺少 jti 声明");
        }

        try {
            Boolean blacklisted = redisOperationsService.hasKey(
                    blacklistKeyPrefix + jwt.getId()
            );
            if (!Boolean.FALSE.equals(blacklisted)) {
                throw invalidToken("访问令牌已注销或状态无法确认");
            }
        } catch (JwtException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new JwtException("无法校验访问令牌黑名单", exception);
        }
        return jwt;
    }

    /**
     * 创建统一的无效访问令牌异常。
     *
     * @param description 校验失败原因
     * @return JWT 校验异常
     */
    private JwtValidationException invalidToken(String description) {
        OAuth2Error error = new OAuth2Error(
                "invalid_token",
                description,
                null
        );
        return new JwtValidationException(description, List.of(error));
    }
}
