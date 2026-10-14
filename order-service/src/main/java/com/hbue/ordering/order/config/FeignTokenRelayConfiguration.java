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
 * <p>负责在同步 HTTP 请求上下文中，将原始请求的 {@code Authorization} 请求头透传给 Feign 下游服务，
 * 使下游资源服务可以继续校验同一用户 JWT。无 HTTP 请求上下文或原请求未携带该头时不添加该头；
 * 其他内部凭据（例如 {@code X-Internal-Token}）由调用方按各自契约显式传入。</p>
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
     * @apiNote 该拦截器只转发认证请求头，不负责签发、解析或校验 Token。
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

            // 只从当前 Servlet 请求复制凭据，不在后台任务或非 HTTP 调用中构造用户身份。
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

            // 保留原始 Authorization 值，使下游沿用自身资源服务器配置完成验签和授权。
            requestTemplate.header(
                    HttpHeaders.AUTHORIZATION,
                    authorization
            );
        };
    }
}
