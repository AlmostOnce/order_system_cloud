package com.hbue.ordering.statistics.config;

import com.hbue.ordering.common.redis.RedisOperationsService;
import com.hbue.ordering.statistics.security.StatisticsSecurityErrorHandler;
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

/**
 * 统计服务资源服务器安全策略。
 *
 * <p>接口只允许通过 RSA 签名、issuer、有效期和 Redis 黑名单校验的 JWT；方法级 CUSTOMER 授权再限制学生数据入口。
 * Redis 黑名单状态无法确认时拒绝请求，不以可用性为由放过已注销令牌。</p>
 */
@Configuration
@EnableMethodSecurity
public class StatisticsSecurityConfig {
    /** 无状态过滤链只开放健康检查、接口文档和带登录态的统计接口，其余路径默认拒绝。 */
    @Bean
    SecurityFilterChain statisticsSecurity(HttpSecurity http, JwtAuthenticationConverter converter,
                                           StatisticsSecurityErrorHandler errors) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable()).httpBasic(basic -> basic.disable())
                .exceptionHandling(handler -> handler.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/statistics/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.authenticationEntryPoint(errors).accessDeniedHandler(errors)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
                .build();
    }

    /** 把 JWT 的 roles 声明映射为 Spring Security 的 ROLE_*，与其他用户端服务一致。 */
    @Bean
    JwtAuthenticationConverter statisticsJwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /** 验签后再检查 jti 黑名单；任何 Redis 访问故障按无效令牌处理。 */
    @Bean
    JwtDecoder statisticsJwtDecoder(ResourceLoader resources,
                                    @Value("${security.jwt.public-key-location}") String keyLocation,
                                    @Value("${security.jwt.issuer}") String issuer,
                                    @Value("${security.jwt.blacklist-key-prefix:auth:access-token:blacklist:}") String blacklistPrefix,
                                    RedisOperationsService redis) {
        try (InputStream input = resources.getResource(keyLocation).getInputStream()) {
            RSAPublicKey key = Objects.requireNonNull(RsaKeyConverters.x509().convert(input), "RSA 公钥不能为空");
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(key).build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
            return token -> {
                Jwt verified = decoder.decode(token);
                if (verified.getId() == null || verified.getId().isBlank()) throw invalidToken("访问令牌缺少 jti 声明");
                try {
                    if (!Boolean.FALSE.equals(redis.hasKey(blacklistPrefix + verified.getId()))) throw invalidToken("访问令牌已注销或状态无法确认");
                } catch (JwtException exception) {
                    throw exception;
                } catch (RuntimeException exception) {
                    throw new JwtException("无法校验访问令牌黑名单", exception);
                }
                return verified;
            };
        } catch (Exception exception) {
            throw new IllegalStateException("读取统计服务 JWT RSA 公钥失败", exception);
        }
    }

    /** 使用标准 OAuth 错误码表示令牌无效，避免把黑名单存储细节泄露给客户端。 */
    private JwtValidationException invalidToken(String description) {
        OAuth2Error error = new OAuth2Error("invalid_token", description, null);
        return new JwtValidationException(description, List.of(error));
    }
}
