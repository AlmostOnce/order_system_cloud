package com.hbue.ordering.payment.channel;

import com.hbue.ordering.payment.dto.request.ChannelNotifyRequest;
import com.hbue.ordering.payment.model.PaymentDO;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 余额支付渠道实现。
 *
 * <p>余额是同步结算渠道：不存在第三方收银台，也没有异步回调，
 * 扣款成功即支付成功。真正的账务（改余额 + 写流水）由 user-service
 * 在同库同事务内完成，本渠道只提供渠道标识与渠道交易号，
 * 扣款编排与支付单状态机由支付业务层负责。</p>
 *
 * <p>这样分层的原因：渠道适配器不应感知幂等状态机与跨服务调用，
 * 否则每新增一个渠道都要复制一遍下单编排逻辑。</p>
 *
 * @author order-system
 * @date 2026-10-14
 */
@Component
public class BalancePaymentChannel implements PaymentChannel {

    /** 余额渠道标识，与 payments.channel_code 对应。 */
    public static final String CHANNEL_CODE = "BALANCE";

    /** 渠道交易号的时间格式。 */
    private static final DateTimeFormatter TRADE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 渠道标识。
     *
     * @return 固定返回 BALANCE
     */
    @Override
    public String channelCode() {
        return CHANNEL_CODE;
    }

    /**
     * 余额渠道下单：无收银台页面，仅生成渠道交易号。
     *
     * @param payment 已创建的支付单
     * @return 渠道下单结果（收银台地址与二维码均为空）
     */
    @Override
    public ChannelPaymentCreation createPayment(PaymentDO payment) {
        return new ChannelPaymentCreation(
                generateChannelTradeNo(),
                null,
                null
        );
    }

    /**
     * 余额渠道无渠道侧状态机，恒返回 UNKNOWN。
     *
     * @param paymentNo 业务支付单号
     * @param channelTradeNo 渠道交易号
     * @return 状态 UNKNOWN 的查询结果
     */
    @Override
    public ChannelQueryResult queryStatus(
            String paymentNo,
            String channelTradeNo
    ) {
        return new ChannelQueryResult("UNKNOWN");
    }

    /**
     * 余额渠道不存在异步通知，一律拒绝验签。
     *
     * @param request 渠道通知请求体
     * @param signature 渠道侧签名
     * @return 恒返回 false
     */
    @Override
    public boolean verifyNotify(
            ChannelNotifyRequest request,
            String signature
    ) {
        return false;
    }

    /**
     * 余额渠道不存在异步通知，解析无意义。
     *
     * @param request 渠道通知请求体
     * @return 不会返回
     */
    @Override
    public ChannelNotifyMessage parseNotify(ChannelNotifyRequest request) {
        // 余额渠道是同步结算渠道，不存在回调解析路径。
        throw new UnsupportedOperationException("余额渠道不支持异步通知");
    }

    /**
     * 生成余额渠道交易号：BAL 前缀 + 时间戳 + 8 位随机数。
     *
     * @return 渠道交易号
     */
    private String generateChannelTradeNo() {
        String time = LocalDateTime.now().format(TRADE_TIME_FORMAT);
        String random = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase();
        return "BAL" + time + random;
    }
}
