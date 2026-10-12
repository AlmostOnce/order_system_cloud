package com.hbue.ordering.payment.vo;

import com.hbue.ordering.payment.model.PaymentDO;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付单状态查询结果视图。
 *
 * @author order-system
 * @date 2026-10-12
 */
@Data
@Builder
public class PaymentStatusVO {

    /** 支付单 ID。 */
    private Long paymentId;

    /** 业务支付单号。 */
    private String paymentNo;

    /** 订单 ID。 */
    private Long orderId;

    /** 订单号快照。 */
    private String orderNo;

    /** 应付金额。 */
    private BigDecimal amount;

    /** 实收金额。 */
    private BigDecimal paidAmount;

    /** 支付单状态。 */
    private String status;

    /** 渠道交易号。 */
    private String channelTradeNo;

    /** 支付成功时间。 */
    private LocalDateTime successTime;

    /** 失败原因或异常备注。 */
    private String failReason;

    /**
     * 从支付单持久化对象构建状态视图。
     *
     * @param paymentDO 支付单持久化对象
     * @return 状态视图
     */
    public static PaymentStatusVO from(PaymentDO paymentDO) {
        return PaymentStatusVO.builder()
                .paymentId(paymentDO.getPaymentId())
                .paymentNo(paymentDO.getPaymentNo())
                .orderId(paymentDO.getOrderId())
                .orderNo(paymentDO.getOrderNo())
                .amount(paymentDO.getAmount())
                .paidAmount(paymentDO.getPaidAmount())
                .status(paymentDO.getStatus())
                .channelTradeNo(paymentDO.getChannelTradeNo())
                .successTime(paymentDO.getSuccessTime())
                .failReason(paymentDO.getFailReason())
                .build();
    }
}
