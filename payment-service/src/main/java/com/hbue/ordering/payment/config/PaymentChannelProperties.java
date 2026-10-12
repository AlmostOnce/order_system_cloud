package com.hbue.ordering.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 模拟支付渠道配置属性。
 *
 * @author order-system
 * @date 2026-10-12
 */
@Data
@Component
@ConfigurationProperties(prefix = "payment.channel")
public class PaymentChannelProperties {

    /** 渠道回调签名密钥，模拟渠道生成端与验签端共用。 */
    private String secret;

    /** 模拟渠道回调 payment-service 的地址。 */
    private String notifyUrl;

    /** 模拟收银台页面的基础地址。 */
    private String checkoutBaseUrl;

    /** 创建支付单后是否自动延迟发起模拟支付。 */
    private boolean autoConfirmEnabled;

    /** 自动发起模拟支付的延迟毫秒数。 */
    private long autoConfirmDelayMs;
}
