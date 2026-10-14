package com.hbue.ordering.payment.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.payment.config.PaymentChannelProperties;
import com.hbue.ordering.payment.dto.request.ChannelNotifyRequest;
import com.hbue.ordering.payment.util.NotifySignatureUtils;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 模拟渠道异步通知发送器。
 *
 * <p>模拟第三方渠道的行为：支付确认后向支付服务的回调入口
 * 异步发送带 HMAC-SHA256 签名的 HTTP 通知，非成功响应时重试。</p>
 *
 * <p>使用 Java 21 虚拟线程执行异步发送，不阻塞确认支付请求；
 * 每次发送独立签名（时间戳取当前毫秒值）。</p>
 *
 * @author order-system
 * @date 2026-10-12
 */
@Component
public class MockChannelNotifier {

    /** 日志记录器。 */
    private static final Logger log = LoggerFactory.getLogger(
            MockChannelNotifier.class
    );

    /** 渠道侧最大重试次数（与真实渠道的异步通知重试语义一致）。 */
    private static final int MAX_RETRY_COUNT = 3;

    /** 重试间隔毫秒数（固定退避即可）。 */
    private static final long RETRY_INTERVAL_MS = 1_000L;

    /** 渠道通知响应的成功状态码（与 ApiResponse 统一码一致）。 */
    private static final String SUCCESS_CODE = "COMMON_000";

    /** JSON 序列化工具。 */
    private final ObjectMapper objectMapper;

    /** 渠道配置属性。 */
    private final PaymentChannelProperties properties;

    /** HTTP 客户端（模拟第三方渠道发起的请求）。 */
    private final HttpClient httpClient;

    /** 异步任务执行器（虚拟线程）。 */
    private final ExecutorService notifyExecutor;

    /**
     * 创建模拟渠道通知发送器。
     *
     * @param objectMapper JSON 序列化工具
     * @param properties 渠道配置属性
     */
    public MockChannelNotifier(
            ObjectMapper objectMapper,
            PaymentChannelProperties properties
    ) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        this.notifyExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * 异步发送模拟支付结果通知。
     *
     * @param paymentNo 业务支付单号
     * @param channelTradeNo 渠道交易号
     * @param paidAmount 实收金额原串
     * @param status 通知结果：SUCCESS 或 FAILED
     */
    public void notifyAsync(
            String paymentNo,
            String channelTradeNo,
            String paidAmount,
            String status
    ) {
        notifyExecutor.execute(() -> notifyWithRetry(
                paymentNo,
                channelTradeNo,
                paidAmount,
                status
        ));
    }

    /**
     * 同步发送通知并在未收到业务成功确认时固定间隔重试。
     *
     * @param paymentNo 业务支付单号
     * @param channelTradeNo 渠道交易号
     * @param paidAmount 实收金额原串
     * @param status 通知结果：SUCCESS 或 FAILED
     */
    void notifyWithRetry(
            String paymentNo,
            String channelTradeNo,
            String paidAmount,
            String status
    ) {
        ChannelNotifyRequest request = new ChannelNotifyRequest();
        request.setPaymentNo(paymentNo);
        request.setChannelTradeNo(channelTradeNo);
        request.setPaidAmount(paidAmount);
        request.setStatus(status);
        request.setTimestamp(System.currentTimeMillis());

        String signature = NotifySignatureUtils.sign(
                properties.getSecret(),
                NotifySignatureUtils.buildCanonicalString(request)
        );

        // 每次重试复用同一份通知和签名，避免重试期间业务载荷变化；时间戳也保持一致。
        for (int attempt = 1; attempt <= MAX_RETRY_COUNT; attempt++) {
            NotifyHttpResponse response = postNotify(request, signature);
            if (response.isSuccess()) {
                return;
            }
            log.warn("模拟渠道通知未成功受理，paymentNo={}，尝试次数={}，HTTP={}，code={}",
                    paymentNo, attempt, response.httpStatus(), response.code());
            if (attempt < MAX_RETRY_COUNT) {
                try {
                    Thread.sleep(RETRY_INTERVAL_MS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        log.error("模拟渠道通知重试耗尽，paymentNo={}，请检查支付服务回调入口", paymentNo);
    }

    /**
     * 发送单次 HTTP 通知。
     *
     * <p>protected 便于单测覆写以模拟渠道侧响应。</p>
     *
     * @param request 渠道通知请求体
     * @param signature 请求签名
     * @return 渠道侧响应摘要
     */
    protected NotifyHttpResponse postNotify(
            ChannelNotifyRequest request,
            String signature
    ) {
        try {
            String body = objectMapper.writeValueAsString(request);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getNotifyUrl()))
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .header("X-Channel-Signature", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> httpResponse = httpClient.send(
                    httpRequest,
                    HttpResponse.BodyHandlers.ofString()
            );

            String code = null;
            try {
                NotificationResponse parsed = objectMapper.readValue(
                        httpResponse.body(),
                        NotificationResponse.class
                );
                code = parsed.code();
            } catch (Exception parseException) {
                log.warn("模拟渠道通知响应解析失败：{}", httpResponse.body());
            }
            return new NotifyHttpResponse(
                    httpResponse.statusCode(),
                    code
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new NotifyHttpResponse(-1, null);
        } catch (Exception exception) {
            log.warn("模拟渠道通知发送异常：{}", exception.getMessage());
            return new NotifyHttpResponse(-1, null);
        }
    }

    /**
     * 释放虚拟线程执行器资源。
     */
    @PreDestroy
    public void shutdown() {
        notifyExecutor.shutdown();
    }

    /**
     * 渠道侧 HTTP 响应摘要，用于判断回调是否已被支付服务业务受理。
     *
     * @param httpStatus HTTP 状态码；发送异常时为 -1
     * @param code 响应体中的业务状态码；无法解析时为 null
     */
    protected record NotifyHttpResponse(int httpStatus, String code) {

        /**
         * 判断渠道侧是否成功受理通知。
         *
         * @return 受理成功时返回 true
         */
        public boolean isSuccess() {
            return httpStatus >= 200 && httpStatus < 300
                    && SUCCESS_CODE.equals(code);
        }
    }

    /**
     * 回调响应体的最小解析模型，仅提取业务状态码判断是否停止重试。
     *
     * @param code 业务状态码
     * @author order-system
     * @date 2026-10-12
     */
    private record NotificationResponse(String code) {
    }
}
