package com.hbue.ordering.payment.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 模拟收银台页面信息视图。
 *
 * @author order-system
 * @date 2026-10-12
 */
@Data
@Builder
public class CheckoutInfoVO {

    /** 业务支付单号。 */
    private String paymentNo;

    /** 订单号快照。 */
    private String orderNo;

    /** 应付金额。 */
    private BigDecimal amount;

    /** 支付单状态。 */
    private String status;
}
