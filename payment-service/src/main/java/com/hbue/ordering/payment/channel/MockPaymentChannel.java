package com.hbue.ordering.payment.channel;

import com.hbue.ordering.payment.config.PaymentChannelProperties;
import com.hbue.ordering.payment.dto.request.ChannelNotifyRequest;
import com.hbue.ordering.payment.model.PaymentDO;
import com.hbue.ordering.payment.util.NotifySignatureUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 模拟支付渠道实现。
 *
 * <p>刻意不使用 Feign 与服务发现——第三方支付渠道本就不在微服务体系内，
 * 这是对真实部署形态的保留。</p>
 *
 * @author order-system
 * @date 2026-10-12
 */
@Component
public class MockPaymentChannel implements PaymentChannel {

    /** 渠道交易号的时间格式。 */
    private static final DateTimeFormatter TRADE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 渠道配置属性。 */
    private final PaymentChannelProperties properties;

    /**
     * 创建模拟支付渠道。
     *
     * @param properties 渠道配置属性
     */
    public MockPaymentChannel(PaymentChannelProperties properties) {
        this.properties = properties;
    }

    /**
     * 渠道标识。
     *
     * @return 固定返回 MOCK
     */
    @Override
    public String channelCode() {
        return "MOCK";
    }

    /**
     * 模拟渠道下单：本地生成交易号与收银台凭证。
     *
     * @param payment 已创建的支付单
     * @return 渠道下单结果
     */
    @Override
    public ChannelPaymentCreation createPayment(PaymentDO payment) {
        return new ChannelPaymentCreation(
                generateChannelTradeNo(),
                properties.getCheckoutBaseUrl()
                        + "/mock/channel/checkout/" + payment.getPaymentNo(),
                "mockpay://pay?paymentNo=" + payment.getPaymentNo()
                        + "&amount=" + payment.getAmount().toPlainString()
        );
    }

    /**
     * 模拟渠道无渠道侧状态机，恒返回 UNKNOWN。
     *
     * @param paymentNo 业务支付单号
     * @param channelTradeNo 渠道交易号
     * @return 状态 UNKNOWN 的查询结果
     */
    @Override
    public ChannelQueryResult queryStatus(
            String paymentNo,
            String channelTradeNo
    ) {
        return new ChannelQueryResult("UNKNOWN");
    }

    /**
     * 校验异步通知签名与时间戳窗口。
     *
     * @param request 渠道通知请求体
     * @param signature 渠道侧携带的十六进制签名
     * @return 验签通过时返回 true
     */
    @Override
    public boolean verifyNotify(
            ChannelNotifyRequest request,
            String signature
    ) {
        return NotifySignatureUtils.verify(
                properties.getSecret(),
                request,
                signature
        );
    }

    /**
     * 将原始通知解析为标准化消息。
     *
     * @param request 渠道通知请求体
     * @return 标准化回调消息
     */
    @Override
    public ChannelNotifyMessage parseNotify(ChannelNotifyRequest request) {
        BigDecimal paidAmount;
        try {
            paidAmount = new BigDecimal(request.getPaidAmount());
        } catch (NumberFormatException exception) {
            // 金额格式非法时交给上层按金额校验失败处理。
            paidAmount = null;
        }
        return new ChannelNotifyMessage(
                request.getPaymentNo(),
                request.getChannelTradeNo(),
                paidAmount,
                "SUCCESS".equalsIgnoreCase(request.getStatus())
        );
    }

    /**
     * 生成模拟渠道交易号：MOCK 前缀 + 时间戳 + 8 位随机数。
     *
     * @return 渠道交易号
     */
    private String generateChannelTradeNo() {
        String time = LocalDateTime.now().format(TRADE_TIME_FORMAT);
        String random = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase();
        return "MOCK" + time + random;
    }
}
