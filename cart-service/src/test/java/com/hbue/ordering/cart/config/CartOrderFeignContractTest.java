package com.hbue.ordering.cart.config;

import com.hbue.ordering.order.api.client.OrderServiceClient;
import feign.Client;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 验证真实 Feign 装配后的契约、Token 透传和实际超时配置，不依赖 Nacos 或外部 HTTP 服务。 */
class CartOrderFeignContractTest {
    /**
     * 从生产 application.yml 读取配置，通过实际 Feign 客户端验证最终生效的请求选项。
     * @throws Exception YAML 读取失败时抛出
     */
    @Test
    void shouldApplyConfiguredTimeoutsAndRelayCustomerToken() throws Exception {
        var sources = new YamlPropertySourceLoader().load("cart", new ClassPathResource("application.yml"));
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FeignAutoConfiguration.class,
                        HttpMessageConvertersAutoConfiguration.class))
                .withUserConfiguration(TestFeignConfig.class)
                .withInitializer(context -> {
                    for (var source : sources) {
                        context.getEnvironment().getPropertySources().addLast(source);
                    }
                })
                .withPropertyValues("spring.cloud.openfeign.client.config.cartOrderServiceClient.url=http://orders.test");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer customer-contract-test");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            runner.run(context -> {
                assertNull(context.getStartupFailure());
                var response = context.getBean(OrderServiceClient.class).getOrder(123L);
                Capture capture = context.getBean(Capture.class);
                assertEquals(2000, capture.options.get().connectTimeoutMillis());
                assertEquals(3000, capture.options.get().readTimeoutMillis());
                assertEquals("http://orders.test/orders/123", capture.request.get().url());
                assertEquals(List.of("Bearer customer-contract-test"),
                        List.copyOf(capture.request.get().headers().get("Authorization")));
                assertEquals(123L, response.getData().orderId());
                assertEquals(11L, response.getData().items().getFirst().productId());
                assertEquals(2, response.getData().items().getFirst().quantity());
            });
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    /** 保存经过实际 Feign 编码后的请求及最终超时选项。 */
    static class Capture {
        final AtomicReference<Request> request = new AtomicReference<>();
        final AtomicReference<Request.Options> options = new AtomicReference<>();
    }

    /** 仅装配远程调用所需组件，不启动业务应用、数据库或 Nacos。 */
    @Configuration(proxyBeanMethods = false)
    @EnableFeignClients(clients = OrderServiceClient.class)
    @Import(CartFeignConfiguration.class)
    static class TestFeignConfig {
        /** 为每个测试上下文创建独立捕获器。 */
        @Bean
        Capture capture() {
            return new Capture();
        }

        /** 用传输边界替身观察真实 Feign 生成的请求，不替换契约和解码器。 */
        @Bean
        Client client(Capture capture) {
            return (request, options) -> {
                capture.request.set(request);
                capture.options.set(options);
                return Response.builder().request(request).status(200)
                        .headers(Map.of("Content-Type", List.of("application/json")))
                        .body("""
                                {"code":"COMMON_000","message":"操作成功","data":{
                                  "orderId":123,"windowId":1,"status":"待支付",
                                  "items":[{"productId":11,"quantity":2,"productName":"鸡肉饭"}]}}
                                """, StandardCharsets.UTF_8).build();
            };
        }
    }
}
