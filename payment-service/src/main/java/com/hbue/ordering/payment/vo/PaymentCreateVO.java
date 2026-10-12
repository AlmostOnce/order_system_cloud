package com.hbue.ordering.payment.vo;

import com.hbue.ordering.payment.channel.PaymentChannel;
import com.hbue.ordering.payment.model.PaymentDO;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 支付单创建结果视图。
 *
 * @author order-system
 * @date 2026-10-12
 */
@Data
@Builder
public class PaymentCreateVO {

    /** 支付单 ID。 */
    private Long paymentId;

    /** 业务支付单号。 */
    private String paymentNo;

    /** 订单 ID。 */
    private Long orderId;

    /** 订单号快照。 */
    private String orderNo;

    /** 支付渠道标识。 */
    private String channelCode;

    /** 应付金额。 */
    private BigDecimal amount;

    /** 支付单状态。 */
    private String status;

    /** 模拟收银台页面地址。 */
    private String checkoutUrl;

    /** 收银台二维码内容。 */
    private String qrCodeContent;

    /**
     * 从支付单持久化对象与渠道下单结果构建视图。
     *
     * @param paymentDO 支付单持久化对象
     * @param creation 渠道下单结果（首次创建与幂等重放均会重新生成）
     * @return 创建结果视图
     */
    public static PaymentCreateVO from(
            PaymentDO paymentDO,
            PaymentChannel.ChannelPaymentCreation creation
    ) {
        return PaymentCreateVO.builder()
                .paymentId(paymentDO.getPaymentId())
                .paymentNo(paymentDO.getPaymentNo())
                .orderId(paymentDO.getOrderId())
                .orderNo(paymentDO.getOrderNo())
                .channelCode(paymentDO.getChannelCode())
                .amount(paymentDO.getAmount())
                .status(paymentDO.getStatus())
                .checkoutUrl(creation.getCheckoutUrl())
                .qrCodeContent(creation.getQrCodeContent())
                .build();
    }
}
