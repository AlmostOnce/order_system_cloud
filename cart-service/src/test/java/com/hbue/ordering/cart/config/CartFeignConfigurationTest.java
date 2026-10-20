package com.hbue.ordering.cart.config;

import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证商品服务调用只透传当前请求已有的身份，不制造跨用户凭据。
 *
 * @author order-system
 * @date 2026-10-17
 */
class CartFeignConfigurationTest {
    /** 清理测试创建的线程请求上下文，避免影响下一条用例。 */
    @AfterEach
    void clearContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    /** 购物车应把当前 Bearer Token 传给商品服务独立验签。 */
    @Test
    void shouldRelayCurrentAuthorization() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer customer-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        RequestTemplate template = new RequestTemplate();
        new CartFeignConfiguration().cartBearerTokenInterceptor().apply(template);
        assertEquals("Bearer customer-token", template.headers().get("Authorization").iterator().next());
    }

    /** 没有同步请求上下文时不应添加任何用户凭据。 */
    @Test
    void shouldNotInventTokenWithoutRequest() {
        RequestContextHolder.resetRequestAttributes();
        RequestTemplate template = new RequestTemplate();
        new CartFeignConfiguration().cartBearerTokenInterceptor().apply(template);
        assertTrue(template.headers().isEmpty());
    }
}
