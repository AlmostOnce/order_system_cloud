package com.hbue.ordering.order.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 订单服务内部调用共享密钥校验器。
 *
 * <p>订单的"待支付到已支付"推进由支付服务在渠道回调验签通过后调用，
 * 该调用没有用户身份，因此不适用用户 JWT，而是使用服务间共享密钥
 * （X-Internal-Token 请求头）表达"哪个系统能做什么"。</p>
 *
 * <p>使用常量时间比较防止时序攻击；该内部接口不在 Gateway 路由白名单内，
 * 外部无法直接访问。</p>
 *
 * @author order-system
 * @date 2026-10-12
 */
@Component
public class InternalTokenVerifier {

    /** 服务间共享密钥，与 payment-service 的 payment.internal.order-token 保持一致。 */
    private final String internalOrderToken;

    /**
     * 创建内部令牌校验器。
     *
     * @param internalOrderToken 服务间共享密钥
     */
    public InternalTokenVerifier(
            @Value("${payment.internal.order-token}")
            String internalOrderToken
    ) {
        this.internalOrderToken = internalOrderToken;
    }

    /**
     * 校验内部调用令牌是否有效。
     *
     * @param token 请求头中的内部令牌
     * @return 令牌有效时返回 true
     */
    public boolean verify(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        // 常量时间比较，避免逐字符比较泄露密钥前缀长度信息。
        return MessageDigest.isEqual(
                internalOrderToken.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8)
        );
    }
}
