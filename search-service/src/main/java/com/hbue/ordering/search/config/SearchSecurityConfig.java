package com.hbue.ordering.search.config;

import com.hbue.ordering.common.redis.RedisOperationsService;
import com.hbue.ordering.search.security.SearchSecurityErrorHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.io.InputStream;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.Objects;

/** 搜索接口使用与其它用户服务相同的 RSA JWT 与 Redis 注销黑名单校验。 */
@Configuration
@EnableMethodSecurity
public class SearchSecurityConfig {
    @Bean
    public SecurityFilterChain searchSecurityFilterChain(HttpSecurity http, JwtAuthenticationConverter converter,
                                                         SearchSecurityErrorHandler errorHandler) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable()).httpBasic(basic -> basic.disable())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(errorHandler).accessDeniedHandler(errorHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/internal/**").permitAll()
                        .requestMatchers("/search/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.authenticationEntryPoint(errorHandler).accessDeniedHandler(errorHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
                .build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    public JwtDecoder jwtDecoder(ResourceLoader resources,
                                @Value("${security.jwt.public-key-location}") String keyLocation,
                                @Value("${security.jwt.issuer}") String issuer,
                                @Value("${security.jwt.blacklist-key-prefix:auth:access-token:blacklist:}") String blacklistPrefix,
                                RedisOperationsService redis) {
        try (InputStream input = resources.getResource(keyLocation).getInputStream()) {
            RSAPublicKey key = Objects.requireNonNull(RsaKeyConverters.x509().convert(input), "RSA 公钥不能为空");
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(key).build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
            return token -> {
                Jwt jwt = decoder.decode(token);
                if (jwt.getId() == null || jwt.getId().isBlank()) throw invalidToken("访问令牌缺少 jti 声明");
                try {
                    if (!Boolean.FALSE.equals(redis.hasKey(blacklistPrefix + jwt.getId()))) throw invalidToken("访问令牌已注销或状态无法确认");
                } catch (JwtException ex) { throw ex; }
                catch (RuntimeException ex) { throw new JwtException("无法校验访问令牌黑名单", ex); }
                return jwt;
            };
        } catch (Exception ex) { throw new IllegalStateException("读取搜索服务 JWT RSA 公钥失败", ex); }
    }

    private JwtValidationException invalidToken(String description) {
        OAuth2Error error = new OAuth2Error("invalid_token", description, null);
        return new JwtValidationException(description, List.of(error));
    }
}
