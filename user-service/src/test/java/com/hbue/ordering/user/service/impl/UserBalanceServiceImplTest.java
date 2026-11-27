package com.hbue.ordering.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.user.api.dto.UserBalanceDeductCommand;
import com.hbue.ordering.user.api.vo.UserBalanceDeductResultVO;
import com.hbue.ordering.user.mapper.BalanceRecordMapper;
import com.hbue.ordering.user.mapper.UserMapper;
import com.hbue.ordering.user.model.BalanceRecordDO;
import com.hbue.ordering.user.model.UserDO;
import com.hbue.ordering.user.service.PayPasswordService;
import com.hbue.ordering.user.service.UserBalanceService;
import com.hbue.ordering.user.vo.UserBalanceVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 余额扣款业务测试：正常扣款记账、业务单据幂等重放、
 * 唯一键冲突按重放处理、余额不足回滚、入参与归属校验。
 */
@ExtendWith(MockitoExtension.class)
class UserBalanceServiceImplTest {

    private static final Long USER_ID = 17L;
    private static final String BIZ_TYPE = "PAYMENT";
    private static final String BIZ_REF = "PAY202610140001AABBCCDD";
    private static final BigDecimal AMOUNT = new BigDecimal("18.50");

    @Mock
    private UserMapper userMapper;

    @Mock
    private BalanceRecordMapper balanceRecordMapper;

    @Mock
    private PayPasswordService payPasswordService;

    @Mock
    private com.hbue.ordering.user.mapper.BalanceSettlementGuardMapper settlementGuard;
    private UserBalanceService userBalanceService;

    @BeforeEach
    void setUp() {
        userBalanceService = new UserBalanceServiceImpl(
                userMapper,
                balanceRecordMapper,
                payPasswordService, settlementGuard
        );
        var guard = new com.hbue.ordering.user.model.BalanceSettlementGuardDO();
        guard.setAmount(AMOUNT);
        guard.setClosed(0);
        org.mockito.Mockito.lenient().when(settlementGuard.lock(USER_ID, BIZ_TYPE, BIZ_REF)).thenReturn(guard);
    }

    // ---------- 扣款 ----------

    @Test
    void shouldDeductBalanceAndWriteRecord() {
        when(balanceRecordMapper.selectOne(any(Wrapper.class)))
                .thenReturn(null);
        when(balanceRecordMapper.insert(any(BalanceRecordDO.class)))
                .thenReturn(1);
        when(userMapper.deductBalance(USER_ID, AMOUNT)).thenReturn(1);
        when(userMapper.selectBalanceForUpdate(USER_ID)).thenReturn(user("81.50"));

        UserBalanceDeductResultVO result =
                userBalanceService.deduct(USER_ID, command(AMOUNT));

        assertEquals(0, AMOUNT.compareTo(result.getAmount()));
        assertEquals(0, new BigDecimal("81.50")
                .compareTo(result.getBalanceAfter()));
        assertFalse(result.getIdempotent());
        verify(payPasswordService).verifyPayPassword(USER_ID, "pay-pass-123456");
        verify(balanceRecordMapper).insert(any(BalanceRecordDO.class));
        // 扣后余额必须用加锁当前读取，避免并发下写出偏高的 balance_after。
        verify(userMapper).selectBalanceForUpdate(USER_ID);
        verify(balanceRecordMapper).updateBalanceAfter(
                any(), eq(new BigDecimal("81.50"))
        );
    }

    @Test
    void shouldReturnIdempotentResultWhenRecordExists() {
        when(balanceRecordMapper.selectOne(any(Wrapper.class)))
                .thenReturn(record("81.50"));

        UserBalanceDeductResultVO result =
                userBalanceService.deduct(USER_ID, command(AMOUNT));

        assertTrue(result.getIdempotent());
        assertEquals(0, new BigDecimal("81.50")
                .compareTo(result.getBalanceAfter()));
        // 已记过账的业务单据不再校验支付密码，也不再扣款。
        verify(payPasswordService, never()).verifyPayPassword(any(), anyString());
        verify(userMapper, never()).deductBalance(any(), any());
        verify(balanceRecordMapper, never()).insert(any(BalanceRecordDO.class));
    }

    @Test
    void shouldTreatDuplicateKeyAsIdempotentReplay() {
        when(balanceRecordMapper.selectOne(any(Wrapper.class)))
                .thenReturn(null);
        when(balanceRecordMapper.insert(any(BalanceRecordDO.class)))
                .thenThrow(new DuplicateKeyException(
                        "uk_balance_records_user_biz"
                ));
        // 冲突后按加锁当前读回查：真实 MySQL 下快照读看不到对端已提交的流水。
        when(balanceRecordMapper.selectPayForUpdate(USER_ID, BIZ_TYPE, BIZ_REF))
                .thenReturn(record("81.50"));

        UserBalanceDeductResultVO result =
                userBalanceService.deduct(USER_ID, command(AMOUNT));

        assertTrue(result.getIdempotent());
        verify(userMapper, never()).deductBalance(any(), any());
        // 冲突后的回查必须走加锁当前读：真实 MySQL 的普通快照读看不到对端在本事务快照之后提交的流水。
        verify(balanceRecordMapper).selectPayForUpdate(USER_ID, BIZ_TYPE, BIZ_REF);
    }

    /**
     * 唯一键冲突后当前读仍查不到业务流水，说明不是同号重放（例如主键等其它约束冲突），
     * 必须把原始异常抛出触发回滚，不能伪装成幂等成功。
     */
    @Test
    void shouldRethrowDuplicateKeyWhenCurrentReadStillMissing() {
        when(balanceRecordMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(balanceRecordMapper.insert(any(BalanceRecordDO.class)))
                .thenThrow(new DuplicateKeyException(
                        "uk_balance_records_user_biz"
                ));
        when(balanceRecordMapper.selectPayForUpdate(USER_ID, BIZ_TYPE, BIZ_REF))
                .thenReturn(null);

        assertThrows(DuplicateKeyException.class,
                () -> userBalanceService.deduct(USER_ID, command(AMOUNT)));

        verify(userMapper, never()).deductBalance(any(), any());
    }

    @Test
    void shouldRejectWhenBalanceInsufficient() {
        when(balanceRecordMapper.selectOne(any(Wrapper.class)))
                .thenReturn(null);
        when(balanceRecordMapper.insert(any(BalanceRecordDO.class)))
                .thenReturn(1);
        when(userMapper.deductBalance(USER_ID, AMOUNT)).thenReturn(0);
        when(userMapper.selectBalanceById(USER_ID)).thenReturn(user("1.00"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userBalanceService.deduct(USER_ID, command(AMOUNT)));

        assertEquals(
                CommonErrorCode.CONFLICT.code(),
                exception.getErrorCode().code()
        );
        // 回滚测试由事务承担，此处确认不再回填余额。
        verify(balanceRecordMapper, never()).updateBalanceAfter(any(), any());
    }

    @Test
    void shouldRejectWhenAmountNotPositive() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> userBalanceService.deduct(
                        USER_ID, command(BigDecimal.ZERO)
                ));

        assertEquals(
                CommonErrorCode.BAD_REQUEST.code(),
                exception.getErrorCode().code()
        );
        verify(balanceRecordMapper, never()).insert(any(BalanceRecordDO.class));
    }

    @Test
    void shouldRejectWhenBizRefMissing() {
        UserBalanceDeductCommand command = command(AMOUNT);
        command.setBizRef(" ");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userBalanceService.deduct(USER_ID, command));

        assertEquals(
                CommonErrorCode.BAD_REQUEST.code(),
                exception.getErrorCode().code()
        );
    }

    // ---------- 余额查询 ----------

    @Test
    void shouldReturnMyBalanceAndPayPasswordState() {
        when(userMapper.selectBalanceById(USER_ID)).thenReturn(user("81.50"));

        UserBalanceVO vo = userBalanceService.getMyBalance(USER_ID);

        assertEquals(0, new BigDecimal("81.50").compareTo(vo.getBalance()));
        assertTrue(vo.getPayPasswordSet());
    }

    @Test
    void shouldHideMissingUserAsNotFound() {
        when(userMapper.selectBalanceById(anyLong())).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userBalanceService.getMyBalance(USER_ID));

        assertEquals(
                CommonErrorCode.NOT_FOUND.code(),
                exception.getErrorCode().code()
        );
    }

    // ---------- 辅助方法 ----------

    /**
     * 构建余额扣款命令。
     */
    private UserBalanceDeductCommand command(BigDecimal amount) {
        UserBalanceDeductCommand command = new UserBalanceDeductCommand();
        command.setBizType(BIZ_TYPE);
        command.setBizRef(BIZ_REF);
        command.setAmount(amount);
        command.setPayPassword("pay-pass-123456");
        command.setRemark("余额支付订单：ORD202610140001");
        return command;
    }

    /**
     * 构建启用状态用户（已设置支付密码）。
     */
    private UserDO user(String balance) {
        UserDO user = new UserDO();
        user.setUserId(USER_ID);
        user.setStatus(1);
        user.setBalance(new BigDecimal(balance));
        user.setPayPasswordHash("pay-hash");
        return user;
    }

    /**
     * 构建既有余额流水。
     */
    private BalanceRecordDO record(String balanceAfter) {
        BalanceRecordDO record = new BalanceRecordDO();
        record.setRecordId(88L);
        record.setUserId(USER_ID);
        record.setChangeType("PAY");
        record.setAmount(AMOUNT);
        record.setBalanceAfter(new BigDecimal(balanceAfter));
        record.setBizType(BIZ_TYPE);
        record.setBizRef(BIZ_REF);
        record.setCreatedAt(LocalDateTime.now());
        return record;
    }

    @Test
    void closedSettlementRejectsLateDeductionWithoutBalanceWrite() {
        var guard = new com.hbue.ordering.user.model.BalanceSettlementGuardDO();
        guard.setAmount(AMOUNT);
        guard.setClosed(1);
        when(settlementGuard.lock(USER_ID, BIZ_TYPE, BIZ_REF)).thenReturn(guard);
        assertThrows(BusinessException.class, () -> userBalanceService.deduct(USER_ID, command(AMOUNT)));
        verify(userMapper, never()).deductBalance(anyLong(), any());
        verify(balanceRecordMapper, never()).insert(any(BalanceRecordDO.class));
    }

    @Test
    void closeWithoutLedgerPreventsFutureDeduction() {
        when(balanceRecordMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        assertEquals("CLOSED", userBalanceService.closeSettlement(USER_ID,
                new com.hbue.ordering.user.api.dto.BalanceCloseCommand(BIZ_TYPE, BIZ_REF, AMOUNT)));
        verify(settlementGuard).close(USER_ID, BIZ_TYPE, BIZ_REF);
        verify(userMapper, never()).deductBalance(anyLong(), any());
    }

    @Test
    void closePreservesAlreadyCommittedPaymentFact() {
        var record = new BalanceRecordDO();
        record.setAmount(AMOUNT);
        when(balanceRecordMapper.selectOne(any(Wrapper.class))).thenReturn(record);
        assertEquals("PAID", userBalanceService.closeSettlement(USER_ID,
                new com.hbue.ordering.user.api.dto.BalanceCloseCommand(BIZ_TYPE, BIZ_REF, AMOUNT)));
        verify(settlementGuard, never()).close(anyLong(), anyString(), anyString());
    }
}
