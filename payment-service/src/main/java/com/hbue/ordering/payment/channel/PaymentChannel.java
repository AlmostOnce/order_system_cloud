package com.hbue.ordering.payment.channel;

import com.hbue.ordering.payment.dto.request.ChannelNotifyRequest;
import com.hbue.ordering.payment.model.PaymentDO;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 支付渠道策略接口。
 *
 * <p>未来接入支付宝/微信等真实渠道时新增实现类，
 * 业务层只依赖本接口与渠道标识路由，不感知渠道差异。</p>
 *
 * @author order-system
 * @date 2026-10-12
 */
public interface PaymentChannel {

    /**
     * 渠道标识，与 payments.channel_code 对应。
     *
     * @return 渠道标识，如 MOCK
     */
    String channelCode();

    /**
     * 渠道下单，取得收银台地址与渠道受理号。
     *
     * <p>真实渠道此处是一次远程下单调用；模拟渠道仅本地生成凭证。</p>
     *
     * @param payment 已创建的支付单（金额与单号快照）
     * @return 渠道下单结果
     */
    ChannelPaymentCreation createPayment(PaymentDO payment);

    /**
     * 主动查询渠道侧支付状态（对账或补偿用）。
     *
     * <p>模拟渠道无渠道侧状态机，恒返回 UNKNOWN；
     * 真实渠道的主动查询用于异步通知丢失时的补偿对账。</p>
     *
     * @param paymentNo 业务支付单号
     * @param channelTradeNo 渠道交易号
     * @return 渠道侧查询结果
     */
    ChannelQueryResult queryStatus(String paymentNo, String channelTradeNo);

    /**
     * 验证异步通知签名与时间戳窗口，不解析业务字段。
     *
     * @param request 渠道通知请求体
     * @param signature 渠道侧携带的十六进制签名
     * @return 验签通过时返回 true
     */
    boolean verifyNotify(ChannelNotifyRequest request, String signature);

    /**
     * 将渠道原始通知解析为标准化消息。
     *
     * @param request 渠道通知请求体
     * @return 标准化回调消息
     */
    ChannelNotifyMessage parseNotify(ChannelNotifyRequest request);

    /**
     * 渠道下单结果。
     */
    @Data
    @AllArgsConstructor
    class ChannelPaymentCreation {

        /** 渠道交易号。 */
        private String channelTradeNo;

        /** 收银台页面地址。 */
        private String checkoutUrl;

        /** 收银台二维码内容（真实渠道为渠道生成的 scheme 或链接）。 */
        private String qrCodeContent;
    }

    /**
     * 渠道侧支付状态查询结果。
     */
    @Data
    @AllArgsConstructor
    class ChannelQueryResult {

        /** 查询结果状态：UNKNOWN/PENDING/SUCCESS/FAILED。 */
        private String status;
    }

    /**
     * 标准化回调消息。
     */
    @Data
    @AllArgsConstructor
    class ChannelNotifyMessage {

        /** 业务支付单号。 */
        private String paymentNo;

        /** 渠道交易号。 */
        private String channelTradeNo;

        /** 实收金额。 */
        private BigDecimal paidAmount;

        /** 是否支付成功。 */
        private boolean success;
    }
}
