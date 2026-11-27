package com.hbue.ordering.support.config;

import com.hbue.ordering.common.redis.RedisOperationsService;
import com.hbue.ordering.common.web.security.JwtUserIdentity;
import com.hbue.ordering.support.security.SupportSecurityErrorHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPublicKey;
import java.util.Objects;

/**
 * 反馈服务的无状态 JWT 资源服务器配置。
 *
 * <p>与其它业务服务保持一致：使用认证服务的 RSA 公钥校验签名、签发方和有效期，
 * 再检查共享 Redis 中的注销黑名单，并通过 {@link JwtUserIdentity} 把公开用户 UUID
 * 换成内部用户编号、校验登录凭据版本。所有对外端点都要求已认证，具体角色在
 * Controller 方法上通过 {@code @PreAuthorize} 再次限制。</p>
 *
 * @author order-system
 * @date 2026-11-23
 */
@Configuration
public class SupportSecurityConfig {

    /**
     * 创建安全过滤链。
     *
     * <p>只放行健康检查和接口文档；业务端点统一要求认证，未声明的路径默认拒绝，
     * 避免新增内部或调试端点时被意外公开。</p>
     *
     * @param http         Spring Security 配置入口
     * @param errorHandler 统一安全错误响应处理器
     * @return 无状态安全过滤链
     * @throws Exception 过滤链无法构建时抛出
     */
    @Bean
    public SecurityFilterChain supportSecurityFilterChain(
            HttpSecurity http, SupportSecurityErrorHandler errorHandler
    ) throws Exception {
        http
                // 使用 Authorization Bearer 凭据，不依赖 Cookie 或服务端会话。
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // 存活探测不访问业务数据，允许匿名用于本地和容器健康检查。
                        .requestMatchers("/support/ping").permitAll()
                        // 账号注销阻断查询属于服务间调用，由共享内部令牌校验，
                        // 不依赖用户 JWT（用户此时可能已进入注销冷静期）。
                        .requestMatchers("/internal/**").permitAll()
                        // 学生与管理接口都在 /support 前缀下，认证后仍按方法级角色校验。
                        .requestMatchers("/support", "/support/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        // 令牌无效或未提供时也使用项目统一响应体，而不是默认空响应。
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    /**
     * 将 JWT 的 roles 声明转换为 Spring Security 角色集合。
     *
     * <p>认证服务签发 {@code roles=[CUSTOMER]} 等数组，不使用默认 scope 声明。</p>
     *
     * @return 角色认证转换器
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /**
     * 创建包含黑名单与身份版本检查的 JWT 解码器。
     *
     * <p>公钥在启动时读取，缺失即启动失败；令牌缺少 jti 或 Redis 状态无法确认时
     * 一律拒绝认证，避免依赖故障被解释为已注销或未注销。</p>
     *
     * @param resourceLoader         公钥资源加载器
     * @param publicKeyLocation      X.509 PEM 公钥地址
     * @param issuer                 可信 JWT 签发方
     * @param redisOperationsService 共享 Redis 操作组件
     * @param blacklistKeyPrefix     与注销服务一致的黑名单 Key 前缀
     * @param userIdentity           公开身份转内部身份的解析组件
     * @return JWT 解码器
     */
    @Bean
    public JwtDecoder supportJwtDecoder(
            ResourceLoader resourceLoader,
            @Value("${security.jwt.public-key-location}") String publicKeyLocation,
            @Value("${security.jwt.issuer}") String issuer,
            RedisOperationsService redisOperationsService,
            @Value("${security.jwt.blacklist-key-prefix:auth:access-token:blacklist:}")
            String blacklistKeyPrefix,
            JwtUserIdentity userIdentity
    ) {
        try (InputStream input = resourceLoader.getResource(publicKeyLocation).getInputStream()) {
            RSAPublicKey publicKey = Objects.requireNonNull(
                    RsaKeyConverters.x509().convert(input), "RSA 公钥不能为空");
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
            // 验签通过后再检查 jti 黑名单，最后换成内部用户编号主体。
            return token -> userIdentity.toInternalPrincipal(
                    decodeAndCheckBlacklist(decoder, token, redisOperationsService, blacklistKeyPrefix));
        } catch (IOException exception) {
            throw new IllegalStateException("读取反馈服务 JWT RSA 公钥失败", exception);
        }
    }

    /**
     * 先验证令牌真实性，再按 jti 校验注销状态。
     *
     * @param decoder                已配置 RSA 与签发方校验的解码器
     * @param token                  客户端提交的 Bearer Token
     * @param redisOperationsService 共享 Redis 操作组件
     * @param blacklistKeyPrefix     黑名单 Key 前缀
     * @return 已通过全部校验的 JWT
     * @throws JwtException 令牌无效、已注销或 Redis 无法确认状态时抛出
     */
    private Jwt decodeAndCheckBlacklist(JwtDecoder decoder, String token,
                                        RedisOperationsService redisOperationsService,
                                        String blacklistKeyPrefix) {
        Jwt jwt = decoder.decode(token);
        if (jwt.getId() == null || jwt.getId().isBlank()) {
            throw new JwtException("访问令牌缺少 jti 声明");
        }
        try {
            // 只有 Redis 明确返回 false 才接受令牌；故障或未知结果均拒绝认证。
            Boolean blacklisted = redisOperationsService.hasKey(blacklistKeyPrefix + jwt.getId());
            if (!Boolean.FALSE.equals(blacklisted)) {
                throw new JwtException("访问令牌已注销或状态无法确认");
            }
        } catch (JwtException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new JwtException("无法校验访问令牌黑名单", exception);
        }
        return jwt;
    }
}
