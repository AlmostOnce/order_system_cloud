package com.hbue.ordering.payment.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付单持久化对象，与 payments 表一一对应。
 *
 * <p>active_flag 为数据库虚拟生成列，不参与业务读写，
 * 因此不出现在该对象中。</p>
 *
 * @author order-system
 * @date 2026-10-12
 */
@Data
@TableName("payments")
public class PaymentDO {

    /** 支付单主键。 */
    @TableId(value = "payment_id", type = IdType.AUTO)
    private Long paymentId;

    /** 业务支付单号。 */
    private String paymentNo;

    /** 订单 ID（跨库逻辑关联）。 */
    private Long orderId;

    /** 订单号快照。 */
    private String orderNo;

    /** 付款用户快照。 */
    private Long userId;

    /** 支付渠道标识。 */
    private String channelCode;

    /** 应付金额（创建时订单快照）。 */
    private BigDecimal amount;

    /** 实收金额。 */
    private BigDecimal paidAmount;

    /** 渠道交易号。 */
    private String channelTradeNo;

    /** 支付单状态码。 */
    private String status;

    /** 失败原因或异常备注。 */
    private String failReason;

    /** 累计收到回调次数。 */
    private Integer notifyCount;

    /** 最近一次回调时间。 */
    private LocalDateTime lastNotifyAt;

    /** 支付成功时间。 */
    private LocalDateTime successTime;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
