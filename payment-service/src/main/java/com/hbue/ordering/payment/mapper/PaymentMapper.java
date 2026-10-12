package com.hbue.ordering.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.payment.model.PaymentDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface PaymentMapper extends BaseMapper<PaymentDO> {

    /**
     * 按业务支付单号查询支付单。
     *
     * @param paymentNo 业务支付单号
     * @return 支付单；不存在时返回 null
     */
    @Select("""
        SELECT *
        FROM payments
        WHERE payment_no = #{paymentNo}
        LIMIT 1
        """)
    PaymentDO selectByPaymentNo(
            @Param("paymentNo") String paymentNo
    );

    /**
     * 查询订单当前进行中（CREATED）的支付单。
     *
     * @param orderId 订单 ID
     * @return 进行中的支付单；不存在时返回 null
     */
    @Select("""
        SELECT *
        FROM payments
        WHERE order_id = #{orderId}
          AND status = 'CREATED'
        LIMIT 1
        """)
    PaymentDO selectActiveByOrderId(
            @Param("orderId") Long orderId
    );

    /**
     * 查询订单最近创建的支付单。
     *
     * @param orderId 订单 ID
     * @return 最近的支付单；不存在时返回 null
     */
    @Select("""
        SELECT *
        FROM payments
        WHERE order_id = #{orderId}
        ORDER BY payment_id DESC
        LIMIT 1
        """)
    PaymentDO selectLatestByOrderId(
            @Param("orderId") Long orderId
    );

    /**
     * 无条件累计回调次数，用于观察渠道重放行为。
     *
     * @param paymentNo 业务支付单号
     * @return 更新行数
     */
    @Update("""
        UPDATE payments
        SET notify_count = notify_count + 1,
            last_notify_at = CURRENT_TIMESTAMP
        WHERE payment_no = #{paymentNo}
        """)
    int touchNotify(
            @Param("paymentNo") String paymentNo
    );

    /**
     * 仅当支付单仍为 CREATED 时标记支付成功（幂等核心）。
     *
     * <p>更新行数为 0 说明并发回调已有赢家或支付单已终态，
     * 调用方必须回读状态判断，不得重复推进订单。</p>
     *
     * @param paymentNo 业务支付单号
     * @param paidAmount 实收金额
     * @param channelTradeNo 渠道交易号
     * @return 更新行数；条件不满足时返回 0
     */
    @Update("""
        UPDATE payments
        SET status = 'SUCCESS',
            paid_amount = #{paidAmount},
            channel_trade_no = #{channelTradeNo},
            success_time = CURRENT_TIMESTAMP,
            updated_at = CURRENT_TIMESTAMP
        WHERE payment_no = #{paymentNo}
          AND status = 'CREATED'
        """)
    int markSuccessIfCreated(
            @Param("paymentNo") String paymentNo,
            @Param("paidAmount") BigDecimal paidAmount,
            @Param("channelTradeNo") String channelTradeNo
    );

    /**
     * 仅当支付单仍为 CREATED 时标记支付失败。
     *
     * @param paymentNo 业务支付单号
     * @param failReason 失败原因
     * @return 更新行数；条件不满足时返回 0
     */
    @Update("""
        UPDATE payments
        SET status = 'FAILED',
            fail_reason = #{failReason},
            updated_at = CURRENT_TIMESTAMP
        WHERE payment_no = #{paymentNo}
          AND status = 'CREATED'
        """)
    int markFailedIfCreated(
            @Param("paymentNo") String paymentNo,
            @Param("failReason") String failReason
    );
}
