package com.hbue.ordering.user.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.user.api.dto.BalanceRefundCommand;
import com.hbue.ordering.user.mapper.*;
import com.hbue.ordering.user.model.*;
import com.hbue.ordering.user.service.BalanceRefundService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/** 用户余额退款记账；锁用户行串行化同一账户的退款，与余额变更、流水同库原子提交。 */
@Service
public class BalanceRefundServiceImpl implements BalanceRefundService {
    private final UserMapper users;
    private final BalanceRecordMapper records;
    public BalanceRefundServiceImpl(UserMapper users,BalanceRecordMapper records) {this.users=users;this.records=records;}
    /** 原支付流水为金额权威，不能仅凭订单服务传入金额无条件充值。远程超时重试复用refundNo。 */
    @Override @Transactional(rollbackFor=Exception.class)
    public String refund(Long userId,BalanceRefundCommand command) {
        if(userId==null || userId<=0 || command==null || command.paymentNo()==null
                || !command.paymentNo().matches("[A-Za-z0-9_-]{1,64}") || command.refundNo()==null
                || !command.refundNo().matches("[a-f0-9]{32}") || command.amount()==null
                || command.amount().signum()<=0 || command.amount().scale()>2
                || command.amount().compareTo(new BigDecimal("99999999.99"))>0) throw bad("退款参数无效");
        UserDO user=users.selectRefundAccountForUpdate(userId);
        if(user==null) throw bad("退款账户不存在");
        BalanceRecordDO existing=records.selectOne(new QueryWrapper<BalanceRecordDO>().eq("user_id",userId)
                .eq("biz_type","PAYMENT_REFUND").eq("biz_ref",command.refundNo()).eq("change_type","REFUND"));
        if(existing!=null) {
            if(!command.paymentNo().equals(existing.getOriginalBizRef()) || command.amount().compareTo(existing.getAmount())!=0)
                throw bad("退款号对应的原支付或金额不一致");
            return command.refundNo();
        }
        // 已入账事实优先于账户当前状态：响应丢失后即使账号停用，同号重试仍能确认成功。
        if(!Integer.valueOf(1).equals(user.getStatus()) || user.getDeletedAt()!=null) throw bad("退款账户暂不可用");
        BalanceRecordDO paid=records.selectOne(new QueryWrapper<BalanceRecordDO>().eq("user_id",userId)
                .eq("biz_type","PAYMENT").eq("biz_ref",command.paymentNo()).eq("change_type","PAY"));
        if(paid==null) throw bad("没有可退回的余额扣款");
        BigDecimal refunded=records.sumRefunded(userId,command.paymentNo());
        if(refunded.add(command.amount()).compareTo(paid.getAmount())>0) throw bad("累计退款超过实际扣款");
        if(users.creditBalance(userId,command.amount())!=1) throw bad("退款入账失败，请稍后重试");
        BalanceRecordDO row=new BalanceRecordDO();row.setUserId(userId);row.setChangeType("REFUND");
        row.setBizType("PAYMENT_REFUND");row.setBizRef(command.refundNo());row.setOriginalBizRef(command.paymentNo());
        row.setAmount(command.amount());row.setBalanceAfter(user.getBalance().add(command.amount()));
        row.setRemark("订单菜品退款");row.setCreatedAt(LocalDateTime.now());
        if(records.insert(row)!=1) throw new IllegalStateException("退款记账失败");
        return command.refundNo();
    }
    private BusinessException bad(String text) {return new BusinessException(CommonErrorCode.CONFLICT,text);}
}
