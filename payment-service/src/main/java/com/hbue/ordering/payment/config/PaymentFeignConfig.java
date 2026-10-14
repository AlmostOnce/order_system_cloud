package com.hbue.ordering.payment.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 服务间内部调用的 Feign 拦截器。
 *
 * <p>按目标路径选择内部共享密钥：调用订单服务 /internal/orders/** 时追加
 * 订单内部令牌；调用用户服务余额扣款接口时，令牌由该客户端以方法参数显式传入
 * （用户服务校验的是另一把 INTERNAL_SCOPE_TOKEN）。刻意按路径区分，
 * 避免同名请求头被重复写入两个不同的值，导致下游取到错误令牌。</p>
 *
 * <p>同时刻意不透传用户 Authorization——内部调用链路没有用户身份，
 * 系统事件调用以共享密钥表达"哪个系统能做什么"。</p>
 *
 * @author order-system
 * @date 2026-10-12
 */
@Component
public class PaymentFeignConfig implements RequestInterceptor {

    /** 内部令牌请求头名称，与各服务内部接口保持一致。 */
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    /** 订单服务内部接口路径前缀。 */
    private static final String ORDER_INTERNAL_PATH = "/internal/orders/";

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
     * 为订单内部调用追加共享密钥请求头；其他内部调用不在此处写入令牌。
     *
     * @param template Feign 请求模板
     */
    @Override
    public void apply(RequestTemplate template) {
        if (template.path() != null
                && template.path().startsWith(ORDER_INTERNAL_PATH)) {
            template.header(INTERNAL_TOKEN_HEADER, internalOrderToken);
        }
    }
}
