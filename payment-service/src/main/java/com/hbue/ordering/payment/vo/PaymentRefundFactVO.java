package com.hbue.ordering.payment.vo;
import java.math.BigDecimal;
/** 供订单服务核对已成功支付事实，不返回支付密码、渠道凭据或用户资料。 */
public record PaymentRefundFactVO(Long userId,BigDecimal paidAmount,String channelCode) {}
