package com.hbue.ordering.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 支付渠道异步通知请求体。
 *
 * <p>金额使用字符串传输，规避 JSON 数字精度歧义（0.1 与 0.10），
 * 服务端使用 BigDecimal 解析后与应付金额 compareTo 比较；
 * 签名规范化串直接使用该字符串原值，保证渠道侧与验签侧输入完全一致。</p>
 *
 * @author order-system
 * @date 2026-10-12
 */
@Data
public class ChannelNotifyRequest {

    /** 业务支付单号。 */
    @NotBlank
    private String paymentNo;

    /** 渠道交易号。 */
    private String channelTradeNo;

    /** 实收金额原串。 */
    @NotBlank
    private String paidAmount;

    /** 通知结果：SUCCESS 或 FAILED。 */
    @NotBlank
    private String status;

    /** 渠道侧时间戳（毫秒），参与签名并用于防重放窗口校验。 */
    private Long timestamp;
}
