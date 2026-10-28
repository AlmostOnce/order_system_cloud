package com.hbue.ordering.comment.config;

import com.hbue.ordering.common.web.security.JwtUserIdentity;
import com.hbue.ordering.comment.security.CommentSecurityErrorHandler;
import com.hbue.ordering.common.redis.RedisOperationsService;
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
 * 评论的无状态 JWT 资源服务器配置。
 *
 * <p>使用认证服务的 RSA 公钥校验签名、签发方与有效期，再查询共享 Redis 黑名单。
 * 评论端点要求 CUSTOMER 角色，服务直连也执行同样的校验。</p>
 *
 * @author order-system
 * @date 2026-10-17
 */
@Configuration
public class CommentSecurityConfig {

    /**
     * 创建安全过滤链并统一处理过滤链内的 401 和 403。
     *
     * @param http Spring Security 配置入口
     * @param errorHandler 统一安全错误响应处理器
     * @return 无状态安全过滤链
     * @throws Exception 安全过滤链无法构建时抛出
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, CommentSecurityErrorHandler errorHandler
    ) throws Exception {
        http
                // 使用 Authorization Bearer 凭据，不依赖 Cookie 或服务端登录会话。
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
                        .requestMatchers("/comments", "/comments/**").authenticated()
                        // 新端点需显式纳入访问规则，避免误开放其它角色或内部入口。
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        // 无效 Bearer Token 在资源服务器过滤器处理，必须在这里也注册统一响应。
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    /**
     * 将 JWT 的 roles 声明转换为 Spring Security 角色集合。
     *
     * @return 角色认证转换器
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        // 与认证服务签发的 roles=[CUSTOMER] 对齐，不使用默认 scope 声明。
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /**
     * 加载外置 RSA 公钥并创建包含黑名单检查的 JWT 解码器。
     *
     * @param resourceLoader 公钥资源加载器
     * @param publicKeyLocation X.509 PEM 公钥地址
     * @param issuer 可信 JWT 签发方
     * @param redisOperationsService 共享 Redis 操作组件
     * @param blacklistKeyPrefix 与注销服务一致的黑名单 Key 前缀
     * @return JWT 解码器
     * @throws IllegalStateException 公钥缺失或无法读取时使启动失败
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
        // 启动时加载固定公钥，重启不会生成新密钥，也不读取认证服务私钥。
        try (InputStream input = resourceLoader.getResource(publicKeyLocation).getInputStream()) {
            RSAPublicKey publicKey = Objects.requireNonNull(
                    RsaKeyConverters.x509().convert(input), "RSA 公钥不能为空");
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
            return token -> userIdentity.toInternalPrincipal(decodeAndCheckBlacklist(decoder, token, redisOperationsService, blacklistKeyPrefix));
        } catch (IOException exception) {
            throw new IllegalStateException("读取评论服务 JWT RSA 公钥失败", exception);
        }
    }

    /**
     * 先验证令牌真实性，再按 jti 校验注销状态。
     *
     * @param decoder 已配置 RSA 与签发方校验的解码器
     * @param token 客户端提交的 Bearer Token
     * @param redisOperationsService 共享 Redis 操作组件
     * @param blacklistKeyPrefix 黑名单 Key 前缀
     * @return 已通过全部校验的 JWT
     * @throws JwtException 令牌无效、已注销或 Redis 无法确认状态时拒绝认证
     */
    private Jwt decodeAndCheckBlacklist(JwtDecoder decoder, String token,
                                        RedisOperationsService redisOperationsService,
                                        String blacklistKeyPrefix) {
        // 无效签名、过期或错误签发方在查询 Redis 前就被拒绝。
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
