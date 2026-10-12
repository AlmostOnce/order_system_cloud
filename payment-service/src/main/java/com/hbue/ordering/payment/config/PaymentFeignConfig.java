package com.hbue.ordering.payment.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 订单服务内部调用的 Feign 拦截器。
 *
 * <p>统一为 /internal/orders/** 调用追加 X-Internal-Token 请求头；
 * 刻意不透传用户 Authorization——回调链路没有用户身份，
 * 系统事件调用以共享密钥表达"哪个系统能做什么"。</p>
 *
 * @author order-system
 * @date 2026-10-12
 */
@Component
public class PaymentFeignConfig implements RequestInterceptor {

    /** 内部令牌请求头名称，与 OrderInternalController 保持一致。 */
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    /** 订单内部接口共享密钥，与 order-service 的 payment.internal.order-token 保持一致。 */
    private final String internalOrderToken;

    /**
     * 创建 Feign 拦截器。
     *
     * @param internalOrderToken 订单内部接口共享密钥
     */
    public PaymentFeignConfig(
            @Value("${payment.internal.order-token}")
            String internalOrderToken
    ) {
        this.internalOrderToken = internalOrderToken;
    }

    /**
     * 为内部调用追加共享密钥请求头。
     *
     * @param template Feign 请求模板
     */
    @Override
    public void apply(RequestTemplate template) {
        template.header(INTERNAL_TOKEN_HEADER, internalOrderToken);
    }
}
