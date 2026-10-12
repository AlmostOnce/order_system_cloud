package com.hbue.ordering.payment.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.payment.config.PaymentChannelProperties;
import com.hbue.ordering.payment.dto.request.ChannelNotifyRequest;
import com.hbue.ordering.payment.util.NotifySignatureUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 模拟渠道通知发送器测试：验证非成功响应时的重试语义。
 */
class MockChannelNotifierTest {

    /** 记录每次发送的签名，用于断言每次重试独立签名。 */
    private final List<String> postedSignatures = new ArrayList<>();

    /** 记录发送次数。 */
    private int postCount;

    /** 桩响应：按次数依次返回。 */
    private final List<MockChannelNotifier.NotifyHttpResponse> responses =
            new ArrayList<>();

    private StubChannelNotifier notifier;

    @BeforeEach
    void setUp() {
        PaymentChannelProperties properties = new PaymentChannelProperties();
        properties.setSecret("test-secret");
        properties.setNotifyUrl("http://127.0.0.1:9101/internal/payments/notify");
        notifier = new StubChannelNotifier(
                new ObjectMapper(),
                properties
        );
    }

    @Test
    void shouldRetryUntilSuccess() {
        responses.add(failure());
        responses.add(success());
        responses.add(success());

        notifier.notifyWithRetry(
                "PAY001", "MOCK001", "18.50", "SUCCESS"
        );

        assertEquals(2, postCount);
    }

    @Test
    void shouldGiveUpAfterMaxRetryWhenAlwaysFailing() {
        responses.add(failure());
        responses.add(failure());
        responses.add(failure());

        notifier.notifyWithRetry(
                "PAY001", "MOCK001", "18.50", "SUCCESS"
        );

        // 最大尝试 3 次：1 次首发 + 2 次重试。
        assertEquals(3, postCount);
    }

    @Test
    void shouldSignEachAttemptIndependently() {
        responses.add(success());

        notifier.notifyWithRetry(
                "PAY001", "MOCK001", "18.50", "SUCCESS"
        );

        assertEquals(1, postedSignatures.size());
        ChannelNotifyRequest request = notifier.lastRequest;
        String expected = NotifySignatureUtils.sign(
                "test-secret",
                NotifySignatureUtils.buildCanonicalString(request)
        );
        assertEquals(expected, postedSignatures.getFirst());
    }

    /**
     * 构建成功受理响应。
     */
    private MockChannelNotifier.NotifyHttpResponse success() {
        return new MockChannelNotifier.NotifyHttpResponse(200, "COMMON_000");
    }

    /**
     * 构建失败响应。
     */
    private MockChannelNotifier.NotifyHttpResponse failure() {
        return new MockChannelNotifier.NotifyHttpResponse(409, "COMMON_409");
    }

    /**
     * 覆写 HTTP 发送的可测试桩。
     */
    private class StubChannelNotifier extends MockChannelNotifier {

        /** 最近一次发送的请求。 */
        private ChannelNotifyRequest lastRequest;

        StubChannelNotifier(
                ObjectMapper objectMapper,
                PaymentChannelProperties properties
        ) {
            super(objectMapper, properties);
        }

        @Override
        protected NotifyHttpResponse postNotify(
                ChannelNotifyRequest request,
                String signature
        ) {
            postCount++;
            lastRequest = request;
            postedSignatures.add(signature);
            return responses.get(postCount - 1);
        }
    }
}
