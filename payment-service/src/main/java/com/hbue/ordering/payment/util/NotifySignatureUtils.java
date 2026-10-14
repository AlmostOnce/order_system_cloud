package com.hbue.ordering.payment.util;

import com.hbue.ordering.payment.dto.request.ChannelNotifyRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 渠道异步通知 HMAC-SHA256 签名工具。
 *
 * <p>模拟渠道的签名生成端（MockChannelNotifier）与支付服务的验签端共用本工具类，
 * 以保证双方使用同一规范化字段顺序和 HMAC-SHA256 算法。本工具只负责签名计算与时间窗判断，
 * 不负责支付单状态、金额与渠道交易号的业务校验。</p>
 *
 * <p>规范化串按固定字段顺序用换行符连接：
 * paymentNo \n channelTradeNo(可空则空串) \n paidAmount原串 \n status \n timestamp。
 * 使用换行分隔规避直接拼接的字段歧义，与订单服务请求摘要的
 * 长度前缀思想同源。</p>
 *
 * @author order-system
 * @date 2026-10-12
 */
public final class NotifySignatureUtils {

    /** 通知时间戳允许的偏移窗口（毫秒），超出即视为重放。 */
    public static final long TIMESTAMP_WINDOW_MS = 300_000L;

    /** HMAC-SHA256 算法名。 */
    private static final String HMAC_SHA256 = "HmacSHA256";

    /**
     * 工具类禁止实例化。
     */
    private NotifySignatureUtils() {
    }

    /**
     * 按约定顺序拼接签名规范化串。
     *
     * @param request 渠道通知请求体
     * @return 规范化串
     */
    public static String buildCanonicalString(ChannelNotifyRequest request) {
        String channelTradeNo = request.getChannelTradeNo() == null
                ? ""
                : request.getChannelTradeNo();
        return request.getPaymentNo()
                + "\n" + channelTradeNo
                + "\n" + request.getPaidAmount()
                + "\n" + request.getStatus()
                + "\n" + request.getTimestamp();
    }

    /**
     * 使用共享密钥对规范化串计算 HMAC-SHA256 十六进制签名。
     *
     * @param secret 共享密钥
     * @param canonicalString 规范化串
     * @return 十六进制签名
     * @throws IllegalStateException HMAC 算法或密钥初始化不可用时抛出
     */
    public static String sign(String secret, String canonicalString) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    HMAC_SHA256
            ));
            byte[] digest = mac.doFinal(
                    canonicalString.getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("计算渠道通知签名失败", exception);
        }
    }

    /**
     * 校验时间戳是否在防重放窗口内。
     *
     * @param timestamp 渠道侧时间戳（毫秒）
     * @return 时间戳有效时返回 true
     */
    public static boolean isTimestampValid(Long timestamp) {
        if (timestamp == null) {
            return false;
        }
        long offset = Math.abs(System.currentTimeMillis() - timestamp);
        return offset <= TIMESTAMP_WINDOW_MS;
    }

    /**
     * 验证渠道通知签名：先拒绝空签名或超出时间窗的请求，再重建规范串计算签名，
     * 最后使用常量时间比较降低时序侧信道风险。
     *
     * @param secret 共享密钥
     * @param request 渠道通知请求体
     * @param signature 渠道侧携带的十六进制签名
     * @return 验签通过时返回 true
     */
    public static boolean verify(
            String secret,
            ChannelNotifyRequest request,
            String signature
    ) {
        if (signature == null || signature.isBlank()
                || !isTimestampValid(request.getTimestamp())) {
            return false;
        }

        String expected = sign(secret, buildCanonicalString(request));
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
        );
    }
}
