package com.hbue.ordering.recommendation.config;

import com.hbue.ordering.common.redis.RedisOperationsService;
import com.hbue.ordering.common.web.security.JwtUserIdentity;
import com.hbue.ordering.recommendation.security.RecommendationSecurityErrorHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
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
import java.io.InputStream;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.Objects;

/** 使用项目 RSA JWT、issuer、jti 黑名单及 CUSTOMER 角色；内部路径只由控制器令牌校验放行。 */
@Configuration
@EnableMethodSecurity
public class RecommendationSecurityConfig {
    /** 只开放健康检查、接口文档、已认证推荐和需要共享密钥的内部入口。 */
    @Bean
    SecurityFilterChain recommendationSecurity(HttpSecurity http, JwtAuthenticationConverter converter,
            RecommendationSecurityErrorHandler errors) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable()).httpBasic(basic -> basic.disable())
                .exceptionHandling(handler -> handler.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/v3/api-docs/**",
                                "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/internal/recommendations/**").permitAll()
                        .requestMatchers("/recommendations/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.authenticationEntryPoint(errors).accessDeniedHandler(errors)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
                .build();
    }

    /** 将 JWT roles 映射为 Spring Security ROLE_*，供首页接口再次执行 CUSTOMER 授权。 */
    @Bean
    JwtAuthenticationConverter recommendationJwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /** 验证 RSA 签名、发行者、有效期及 Redis 注销黑名单；黑名单不可查时拒绝登录。 */
    @Bean
    JwtDecoder recommendationJwtDecoder(ResourceLoader resources,
            @Value("${security.jwt.public-key-location}") String keyLocation,
            @Value("${security.jwt.issuer}") String issuer,
            @Value("${security.jwt.blacklist-key-prefix:auth:access-token:blacklist:}") String blacklistPrefix,
            RedisOperationsService redis, JwtUserIdentity identities) {
        try (InputStream input = resources.getResource(keyLocation).getInputStream()) {
            RSAPublicKey key = Objects.requireNonNull(RsaKeyConverters.x509().convert(input), "RSA公钥不能为空");
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(key).build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
            return token -> {
                Jwt verified = decoder.decode(token);
                if (verified.getId() == null || verified.getId().isBlank()) throw invalidToken();
                try {
                    if (!Boolean.FALSE.equals(redis.hasKey(blacklistPrefix + verified.getId()))) throw invalidToken();
                } catch (JwtException exception) {
                    throw exception;
                } catch (RuntimeException exception) {
                    throw new JwtException("无法校验访问令牌黑名单", exception);
                }
                // 认证阶段把公开UUID subject 解析为当前内部用户主键，接口层不会重复查询身份服务。
                return identities.toInternalPrincipal(verified);
            };
        } catch (Exception exception) {
            throw new IllegalStateException("读取推荐服务 JWT RSA 公钥失败", exception);
        }
    }

    /** 使用标准 invalid_token 错误，避免向调用方暴露令牌或黑名单存储细节。 */
    private JwtValidationException invalidToken() {
        OAuth2Error error = new OAuth2Error("invalid_token", "访问令牌无效或已注销", null);
        return new JwtValidationException("访问令牌无效", List.of(error));
    }
}
