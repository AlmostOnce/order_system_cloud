package com.hbue.ordering.user.api.client;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/** 仅供 UserServiceClient 的 Feign 子上下文使用，不注册为全局拦截器。 */
public class UserServiceInternalConfiguration {
    @Bean
    public RequestInterceptor userInternalToken(@Value("${internal.scope.token:}") String token) {
        return template -> {
            if (token == null || token.isBlank()) throw new IllegalStateException("用户服务内部调用凭据未配置");
            template.header("X-Internal-Token", token);
        };
    }
}
