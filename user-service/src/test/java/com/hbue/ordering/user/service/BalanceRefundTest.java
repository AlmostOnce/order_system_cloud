package com.hbue.ordering.user.service;

import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.user.api.dto.BalanceRefundCommand;
import com.hbue.ordering.user.mapper.BalanceRecordMapper;
import com.hbue.ordering.user.mapper.UserMapper;
import com.hbue.ordering.user.model.BalanceRecordDO;
import com.hbue.ordering.user.model.UserDO;
import com.hbue.ordering.user.service.impl.BalanceRefundServiceImpl;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 金额及幂等边界测试；本地事务原子性另由数据库及运行联调验证，不用Mock假装事务已回滚。 */
class BalanceRefundTest {
    private final UserMapper users=mock(UserMapper.class);
    private final BalanceRecordMapper records=mock(BalanceRecordMapper.class);
    private final BalanceRefundServiceImpl service=new BalanceRefundServiceImpl(users,records);
    private final String number="a".repeat(32);
    /** 所有退款先锁定原账户，重复号也必须核对原支付及金额。 */
    private void account() {
        var user=new UserDO();user.setStatus(1);user.setBalance(new BigDecimal("20.00"));
        when(users.selectRefundAccountForUpdate(7L)).thenReturn(user);
    }
    @Test void sameRefundReturnsWithoutCreditingAgain() {
        account();var old=new BalanceRecordDO();old.setOriginalBizRef("PAY1");old.setAmount(new BigDecimal("3.00"));
        when(records.selectOne(any())).thenReturn(old);
        // 已入账后账号停用不改变退款事实，重试仍返回成功且不能再次加钱。
        var disabled=new UserDO();disabled.setStatus(0);disabled.setDeletedAt(java.time.LocalDateTime.now());when(users.selectRefundAccountForUpdate(7L)).thenReturn(disabled);
        assertEquals(number,service.refund(7L,new BalanceRefundCommand("PAY1",number,new BigDecimal("3.00"))));
        verify(users,never()).creditBalance(any(),any());verify(records,never()).insert(any(BalanceRecordDO.class));
        assertThrows(BusinessException.class,()->service.refund(7L,new BalanceRefundCommand("PAY2",number,new BigDecimal("3.00"))));
    }
    /** 两笔不同退款号仍受同一原支付总额限制，不能用换号突破实付上限。 */
    @Test void cumulativeRefundCannotExceedOriginalDebit() {
        account();var paid=new BalanceRecordDO();paid.setAmount(new BigDecimal("10.00"));
        when(records.selectOne(any())).thenReturn(null,paid);
        when(records.sumRefunded(7L,"PAY1")).thenReturn(new BigDecimal("8.00"));
        assertThrows(BusinessException.class,()->service.refund(7L,new BalanceRefundCommand("PAY1",number,new BigDecimal("3.00"))));
        verify(users,never()).creditBalance(any(),any());
    }
    /** 没有真实余额扣款的支付记录不能当作充值来源。 */
    @Test void absentDebitAndInvalidPrecisionNeverCredit() {
        account();assertThrows(BusinessException.class,()->service.refund(7L,new BalanceRefundCommand("PAY1",number,new BigDecimal("1.00"))));
        assertThrows(BusinessException.class,()->service.refund(7L,new BalanceRefundCommand("PAY1",number,new BigDecimal("0.001"))));
        verify(users,never()).creditBalance(any(),any());
    }
}
