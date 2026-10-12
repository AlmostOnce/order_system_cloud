package com.hbue.ordering.payment.channel;

import com.hbue.ordering.payment.config.PaymentChannelProperties;
import com.hbue.ordering.payment.dto.request.ChannelNotifyRequest;
import com.hbue.ordering.payment.model.PaymentDO;
import com.hbue.ordering.payment.util.NotifySignatureUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模拟支付渠道实现测试。
 */
class MockPaymentChannelTest {

    private static final String SECRET = "test-secret";

    private PaymentChannelProperties properties;

    private MockPaymentChannel channel;

    @BeforeEach
    void setUp() {
        properties = new PaymentChannelProperties();
        properties.setSecret(SECRET);
        properties.setCheckoutBaseUrl("http://127.0.0.1:9101");
        channel = new MockPaymentChannel(properties);
    }

    @Test
    void shouldCreateMockPaymentWithCheckoutUrl() {
        PaymentDO payment = payment("PAY202610120001AABBCCDD");

        PaymentChannel.ChannelPaymentCreation creation =
                channel.createPayment(payment);

        assertTrue(creation.getChannelTradeNo().startsWith("MOCK"));
        assertEquals(
                "http://127.0.0.1:9101/mock/channel/checkout/"
                        + payment.getPaymentNo(),
                creation.getCheckoutUrl()
        );
        assertTrue(creation.getQrCodeContent()
                .contains(payment.getPaymentNo()));
    }

    @Test
    void shouldAlwaysReturnUnknownForQueryStatus() {
        assertEquals(
                "UNKNOWN",
                channel.queryStatus("PAY001", "MOCK001").getStatus()
        );
    }

    @Test
    void shouldVerifyAndParseSignedNotify() {
        ChannelNotifyRequest request = notify("18.50", "SUCCESS");
        String signature = NotifySignatureUtils.sign(
                SECRET,
                NotifySignatureUtils.buildCanonicalString(request)
        );

        assertTrue(channel.verifyNotify(request, signature));

        PaymentChannel.ChannelNotifyMessage message =
                channel.parseNotify(request);
        assertEquals("PAY001", message.getPaymentNo());
        assertEquals(
                0,
                new BigDecimal("18.50").compareTo(message.getPaidAmount())
        );
        assertTrue(message.isSuccess());
    }

    @Test
    void shouldParseIllegalAmountAsNull() {
        PaymentChannel.ChannelNotifyMessage message =
                channel.parseNotify(notify("abc", "FAILED"));

        assertFalse(message.isSuccess());
        assertNull(message.getPaidAmount());
    }

    @Test
    void shouldReturnMockChannelCode() {
        assertEquals("MOCK", channel.channelCode());
    }

    /**
     * 构建支付单。
     */
    private PaymentDO payment(String paymentNo) {
        PaymentDO payment = new PaymentDO();
        payment.setPaymentNo(paymentNo);
        payment.setOrderId(501L);
        payment.setOrderNo("ORD202610120001AABBCCDD");
        payment.setAmount(new BigDecimal("18.50"));
        payment.setStatus("CREATED");
        payment.setCreatedAt(LocalDateTime.now());
        return payment;
    }

    /**
     * 构建通知请求。
     */
    private ChannelNotifyRequest notify(String paidAmount, String status) {
        ChannelNotifyRequest request = new ChannelNotifyRequest();
        request.setPaymentNo("PAY001");
        request.setChannelTradeNo("MOCK001");
        request.setPaidAmount(paidAmount);
        request.setStatus(status);
        request.setTimestamp(System.currentTimeMillis());
        return request;
    }
}
