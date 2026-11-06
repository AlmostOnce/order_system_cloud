package com.hbue.ordering.payment.service;
import com.hbue.ordering.payment.vo.PaymentRefundFactVO;
import java.math.BigDecimal;
/** 支付退款门禁：确认成功余额支付后，交给用户服务按真实扣款幂等入账。 */
public interface PaymentRefundService {
    PaymentRefundFactVO fact(Long orderId);
    String refund(Long orderId,String refundNo,BigDecimal amount);
}
