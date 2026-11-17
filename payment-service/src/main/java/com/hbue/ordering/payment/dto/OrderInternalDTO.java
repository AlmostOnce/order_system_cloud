package com.hbue.ordering.payment.dto;

import lombok.Data;

/**
 * 订单服务内部订单信息传输对象。
 *
 * <p>接口不返回 DO，跨服务传输使用独立 DTO；
 * 字段与 order-service 的 OrderInternalVO 保持一致。</p>
 *
 * @author order-system
 * @date 2026-10-12
 */
@Data
public class OrderInternalDTO {

    private Long paymentExpiresAt;
    private Long serverTime;

    /** 订单 ID。 */
    private Long orderId;

    /** 订单号。 */
    private String orderNo;

    /** 下单用户 ID。 */
    private Long userId;

    /** 订单总金额。 */
    private java.math.BigDecimal totalAmount;

    /** 服务端已确认的优惠金额快照。 */
    private java.math.BigDecimal discountAmount;

    /** 服务端已确认的应付金额快照，支付单必须使用该字段。 */
    private java.math.BigDecimal payableAmount;

    /** 订单当前状态（数据库中文值）。 */
    private String status;
}
