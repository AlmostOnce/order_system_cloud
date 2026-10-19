package com.hbue.ordering.cart.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 购物车代表当前顾客调用商品、订单服务时的凭据透传配置。
 *
 * <p>只转发当前请求已有的 Authorization，不签发新 Token，不在后台调用中模拟用户。</p>
 *
 * @author order-system
 * @date 2026-10-17
 */
@Configuration
public class CartFeignConfiguration {
    /**
     * 将当前同步请求的 Bearer Token 传给商品核价、订单归属验证接口。
     *
     * @return Feign 请求拦截器
     */
    @Bean
    public RequestInterceptor cartBearerTokenInterceptor() {
        return template -> {
            // Feign 同步调用沿用 Servlet 请求上下文；没有上下文时不添加任何身份凭据。
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
                String authorization = attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
                if (authorization != null && !authorization.isBlank()) {
                    template.header(HttpHeaders.AUTHORIZATION, authorization);
                }
            }
        };
    }
}
