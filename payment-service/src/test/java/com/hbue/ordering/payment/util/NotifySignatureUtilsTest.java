package com.hbue.ordering.payment.util;

import com.hbue.ordering.payment.dto.request.ChannelNotifyRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 渠道通知签名工具测试：覆盖模拟渠道生成端与验签端共用的一致性。
 */
class NotifySignatureUtilsTest {

    private static final String SECRET = "mock_channel_secret_dev_2026";

    @Test
    void shouldVerifySignatureGeneratedByChannelSide() {
        ChannelNotifyRequest request = request(
                "PAY202610120001AABBCCDD",
                "MOCK2026101200019988",
                "18.50",
                "SUCCESS",
                System.currentTimeMillis()
        );
        String signature = NotifySignatureUtils.sign(
                SECRET,
                NotifySignatureUtils.buildCanonicalString(request)
        );

        assertTrue(NotifySignatureUtils.verify(SECRET, request, signature));
    }

    @Test
    void shouldRejectTamperedField() {
        ChannelNotifyRequest request = request(
                "PAY202610120001AABBCCDD",
                "MOCK2026101200019988",
                "18.50",
                "SUCCESS",
                System.currentTimeMillis()
        );
        String signature = NotifySignatureUtils.sign(
                SECRET,
                NotifySignatureUtils.buildCanonicalString(request)
        );

        // 篡改金额：签名不再匹配。
        request.setPaidAmount("0.01");
        assertFalse(NotifySignatureUtils.verify(SECRET, request, signature));
    }

    @Test
    void shouldRejectStaleTimestampOutsideReplayWindow() {
        ChannelNotifyRequest request = request(
                "PAY202610120001AABBCCDD",
                "MOCK2026101200019988",
                "18.50",
                "SUCCESS",
                System.currentTimeMillis()
                - NotifySignatureUtils.TIMESTAMP_WINDOW_MS - 1_000L
        );
        String signature = NotifySignatureUtils.sign(
                SECRET,
                NotifySignatureUtils.buildCanonicalString(request)
        );

        assertFalse(NotifySignatureUtils.verify(SECRET, request, signature));
    }

    @Test
    void shouldRejectNullSignatureAndTimestamp() {
        ChannelNotifyRequest request = request(
                "PAY202610120001AABBCCDD",
                null,
                "18.50",
                "SUCCESS",
                System.currentTimeMillis()
        );

        assertFalse(NotifySignatureUtils.verify(SECRET, request, null));
        assertFalse(NotifySignatureUtils.verify(SECRET, request, " "));
        request.setTimestamp(null);
        assertFalse(NotifySignatureUtils.isTimestampValid(null));
    }

    @Test
    void shouldDistinguishSecrets() {
        ChannelNotifyRequest request = request(
                "PAY202610120001AABBCCDD",
                null,
                "18.50",
                "SUCCESS",
                System.currentTimeMillis()
        );
        String signature = NotifySignatureUtils.sign(
                SECRET,
                NotifySignatureUtils.buildCanonicalString(request)
        );
        String otherSignature = NotifySignatureUtils.sign(
                "another-secret",
                NotifySignatureUtils.buildCanonicalString(request)
        );

        assertNotEquals(signature, otherSignature);
        assertFalse(NotifySignatureUtils.verify(
                "another-secret", request, signature
        ));
    }

    @Test
    void shouldBuildCanonicalStringWithNewlineSeparator() {
        ChannelNotifyRequest request = request(
                "PAY001",
                null,
                "18.50",
                "SUCCESS",
                1760251805000L
        );

        assertEquals(
                "PAY001\n\n18.50\nSUCCESS\n1760251805000",
                NotifySignatureUtils.buildCanonicalString(request)
        );
    }

    /**
     * 构建通知请求。
     */
    private ChannelNotifyRequest request(
            String paymentNo,
            String channelTradeNo,
            String paidAmount,
            String status,
            Long timestamp
    ) {
        ChannelNotifyRequest request = new ChannelNotifyRequest();
        request.setPaymentNo(paymentNo);
        request.setChannelTradeNo(channelTradeNo);
        request.setPaidAmount(paidAmount);
        request.setStatus(status);
        request.setTimestamp(timestamp);
        return request;
    }
}
