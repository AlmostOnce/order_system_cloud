package com.hbue.ordering.notification.config;

import com.hbue.ordering.common.redis.RedisOperationsService;
import com.hbue.ordering.common.web.security.JwtUserIdentity;
import java.io.InputStream;
import java.security.interfaces.RSAPublicKey;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class NotificationSecurityConfig {
    @Bean
    SecurityFilterChain notificationSecurity(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(form -> form.disable()).httpBasic(basic -> basic.disable())
            .authorizeHttpRequests(a -> a.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                    .requestMatchers("/notifications/ws").permitAll()
                    .requestMatchers("/notifications", "/notifications/**").authenticated().anyRequest().denyAll())
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {}));
        return http.build();
    }
    @Bean
    JwtDecoder notificationJwtDecoder(ResourceLoader loader, @Value("${security.jwt.public-key-location}") String location,
            @Value("${security.jwt.issuer}") String issuer, @Value("${security.jwt.blacklist-key-prefix:auth:access-token:blacklist:}") String prefix,
            RedisOperationsService redis, JwtUserIdentity identity) throws Exception {
        try (InputStream stream = loader.getResource(location).getInputStream()) {
            RSAPublicKey key = Objects.requireNonNull(RsaKeyConverters.x509().convert(stream));
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(key).build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
            return token -> {
                Jwt verified = decoder.decode(token);
                if (verified.getId() == null || verified.getId().isBlank()) throw new JwtException("访问令牌缺少标识");
                try {
                    if (!Boolean.FALSE.equals(redis.hasKey(prefix + verified.getId()))) throw new JwtException("访问令牌已注销或状态无法确认");
                } catch (JwtException error) { throw error; }
                catch (RuntimeException error) { throw new JwtException("无法校验访问令牌", error); }
                return identity.toInternalPrincipal(verified);
            };
        }
    }
}
