package com.hbue.ordering.payment.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.payment.mapper.PaymentMapper;
import com.hbue.ordering.payment.model.PaymentDO;
import com.hbue.ordering.payment.service.PaymentRefundService;
import com.hbue.ordering.payment.vo.PaymentRefundFactVO;
import com.hbue.ordering.user.api.client.UserBalanceInternalClient;
import com.hbue.ordering.user.api.dto.BalanceRefundCommand;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
/** 支付单保留SUCCESS这一支付事实；退款事实单独记账，不把部分退款篡改成支付失败。 */
@Service
public class PaymentRefundServiceImpl implements PaymentRefundService {
    private final PaymentMapper payments; private final UserBalanceInternalClient users; private final String token;
    public PaymentRefundServiceImpl(PaymentMapper payments,UserBalanceInternalClient users,@Value("${internal.scope.token:}") String token) {this.payments=payments;this.users=users;this.token=token;}
    /** 只支持实际余额渠道；MOCK没有原始扣款，不能凭它向账户凭空发钱。 */
    private PaymentDO paid(Long orderId) {
        if(orderId==null || orderId<=0) throw bad("订单编号无效");
        PaymentDO row=payments.selectOne(new QueryWrapper<PaymentDO>().eq("order_id",orderId).eq("status","SUCCESS").orderByDesc("payment_id").last("LIMIT 1"));
        if(row==null || !"BALANCE".equals(row.getChannelCode()) || row.getPaidAmount()==null || row.getPaidAmount().signum()<=0)
            throw bad("当前订单没有支持退款的余额支付记录");
        return row;
    }
    /** 只返回已确认余额实付，不暴露支付明细或信任订单请求体提供的收款人。 */
    @Override public PaymentRefundFactVO fact(Long orderId) {PaymentDO row=paid(orderId);return new PaymentRefundFactVO(row.getUserId(),row.getPaidAmount(),row.getChannelCode());}
    /** 不包裹跨服务数据库事务；下游超时由订单退款任务同号重试，用户流水是入账权威。 */
    @Override public String refund(Long orderId,String refundNo,BigDecimal amount) {
        PaymentDO row=paid(orderId);
        if(amount==null || amount.signum()<=0 || amount.scale()>2 || amount.compareTo(row.getPaidAmount())>0) throw bad("退款金额无效");
        var result=users.refund(row.getUserId(),token,new BalanceRefundCommand(row.getPaymentNo(),refundNo,amount));
        if(result==null || !"COMMON_000".equals(result.getCode()) || !refundNo.equals(result.getData()))
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE,"退款入账尚未确认");
        return refundNo;
    }
    private BusinessException bad(String text) {return new BusinessException(CommonErrorCode.CONFLICT,text);}
}
