package com.hbue.ordering.order.config;

import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feign Token 透传配置。
 *
 * <p>负责将客户端请求中的 Authorization 请求头，
 * 透传到下游服务（用户服务和商品服务）。</p>
 *
 * <p>该配置只放在具体业务服务中，
 * 不放入 user-api 契约模块，避免契约模块反向依赖业务服务。</p>
 *
 * @author order-system
 * @date 2026-09-21
 */
@Configuration
public class FeignTokenRelayConfiguration {

    /**
     * 创建 Feign 请求拦截器。
     *
     * @return Feign 请求拦截器
     */
    @Bean
    public RequestInterceptor bearerTokenRequestInterceptor() {
        return requestTemplate -> {
            // 获取当前 HTTP 请求上下文。
            RequestAttributes requestAttributes =
                    RequestContextHolder.getRequestAttributes();

            // 当前调用不属于 HTTP 请求时，不执行 Token 透传。
            if (!(requestAttributes
                    instanceof ServletRequestAttributes servletAttributes)) {
                return;
            }

            // 获取当前客户端请求。
            HttpServletRequest request =
                    servletAttributes.getRequest();

            // 获取客户端传入的 Authorization 请求头。
            String authorization =
                    request.getHeader(HttpHeaders.AUTHORIZATION);

            // 客户端没有携带 Token 时，不向下游添加请求头。
            if (authorization == null
                    || authorization.isBlank()) {
                return;
            }

            // 将 Bearer Token 透传给下游服务。
            requestTemplate.header(
                    HttpHeaders.AUTHORIZATION,
                    authorization
            );
        };
    }
}
