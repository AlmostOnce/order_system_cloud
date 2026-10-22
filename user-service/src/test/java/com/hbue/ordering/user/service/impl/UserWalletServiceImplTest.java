package com.hbue.ordering.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.user.dto.request.RechargeRequest;
import com.hbue.ordering.user.mapper.BalanceRecordMapper;
import com.hbue.ordering.user.mapper.UserMapper;
import com.hbue.ordering.user.model.BalanceRecordDO;
import com.hbue.ordering.user.model.UserDO;
import com.hbue.ordering.user.service.PayPasswordService;
import com.hbue.ordering.user.vo.BalanceRecordPageVO;
import com.hbue.ordering.user.vo.BalanceRecordVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserWalletServiceImplTest {
    @Mock private UserMapper userMapper;
    @Mock private BalanceRecordMapper recordMapper;
    @Mock private PayPasswordService payPasswordService;

    @Test
    void shouldCreditAndWriteRechargeRecordAfterPasswordVerification() {
        when(userMapper.selectBalanceForUpdate(1L)).thenReturn(user("20.00"));
        when(userMapper.creditBalance(1L, new BigDecimal("50.00"))).thenReturn(1);
        when(userMapper.selectBalanceById(1L)).thenReturn(user("70.00"));
        when(recordMapper.insert(any(BalanceRecordDO.class))).thenAnswer(invocation -> {
            BalanceRecordDO record = invocation.getArgument(0);
            record.setRecordId(9L);
            return 1;
        });

        BalanceRecordVO result = service().recharge(1L, "recharge-test-1", request("50.00"));

        verify(payPasswordService).verifyPayPassword(1L, "123456");
        assertEquals(9L, result.recordId());
        assertEquals("RECHARGE", result.changeType());
        assertEquals(new BigDecimal("70.00"), result.balanceAfter());
        ArgumentCaptor<BalanceRecordDO> captor = ArgumentCaptor.forClass(BalanceRecordDO.class);
        verify(recordMapper).insert(captor.capture());
        assertEquals(1L, captor.getValue().getUserId());
        assertEquals("MOCK_RECHARGE", captor.getValue().getBizType());
    }

    @Test
    void shouldReplaySameRequestWithoutCreditingAgain() {
        when(userMapper.selectBalanceForUpdate(1L)).thenReturn(user("70.00"));
        when(recordMapper.selectOne(any())).thenReturn(record(9L, "50.00"));

        assertEquals(9L, service().recharge(1L, "recharge-test-1", request("50.00")).recordId());
        verify(userMapper, never()).creditBalance(anyLong(), any());
        verify(payPasswordService, never()).verifyPayPassword(anyLong(), any());
    }

    @Test
    void shouldRejectDifferentAmountWithSameKey() {
        when(userMapper.selectBalanceForUpdate(1L)).thenReturn(user("70.00"));
        when(recordMapper.selectOne(any())).thenReturn(record(9L, "50.00"));
        BusinessException error = assertThrows(BusinessException.class,
                () -> service().recharge(1L, "recharge-test-1", request("20.00")));
        assertEquals(CommonErrorCode.CONFLICT.code(), error.getErrorCode().code());
        verify(userMapper, never()).creditBalance(anyLong(), any());
    }

    @Test
    void shouldNotCreditWhenPasswordInvalidOrNotSet() {
        when(userMapper.selectBalanceForUpdate(1L)).thenReturn(user("20.00"));
        doThrow(new BusinessException(CommonErrorCode.CONFLICT, "尚未设置支付密码"))
                .when(payPasswordService).verifyPayPassword(1L, "123456");
        assertThrows(BusinessException.class,
                () -> service().recharge(1L, "recharge-test-1", request("50.00")));
        verify(userMapper, never()).creditBalance(anyLong(), any());
        verify(recordMapper, never()).insert(any(BalanceRecordDO.class));
    }

    @Test
    void shouldNotWriteLedgerWhenBalanceLimitRejectsCredit() {
        when(userMapper.selectBalanceForUpdate(1L)).thenReturn(user("99999999.99"));
        assertThrows(BusinessException.class,
                () -> service().recharge(1L, "recharge-test-1", request("50.00")));
        verify(recordMapper, never()).insert(any(BalanceRecordDO.class));
    }

    @Test
    void shouldRejectInvalidAmountsBeforeWriting() {
        for (String amount : List.of("0", "-1", "1.001", "10000.01")) {
            assertThrows(BusinessException.class,
                    () -> service().recharge(1L, "recharge-test-1", request(amount)));
        }
        verify(userMapper, never()).selectBalanceForUpdate(anyLong());
    }

    @Test
    void shouldThrowWhenLedgerInsertFailsAndRequireTransactionRollback() throws NoSuchMethodException {
        when(userMapper.selectBalanceForUpdate(1L)).thenReturn(user("20.00"));
        when(userMapper.creditBalance(1L, new BigDecimal("50.00"))).thenReturn(1);
        when(userMapper.selectBalanceById(1L)).thenReturn(user("70.00"));
        when(recordMapper.insert(any(BalanceRecordDO.class))).thenReturn(0);
        assertThrows(BusinessException.class,
                () -> service().recharge(1L, "recharge-test-1", request("50.00")));
        Transactional transaction = UserWalletServiceImpl.class
                .getMethod("recharge", Long.class, String.class, RechargeRequest.class)
                .getAnnotation(Transactional.class);
        assertNotNull(transaction);
        assertEquals(Exception.class, transaction.rollbackFor()[0]);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldScopeCursorPageToOwnerAndRequestedRecordType() {
        when(userMapper.selectBalanceById(1L)).thenReturn(user("70.00"));
        when(recordMapper.selectList(any())).thenReturn(List.of(record(9L, "10.00"), record(8L, "20.00")));

        BalanceRecordPageVO page = service().listRecords(1L, "RECHARGE", 10L, 1);

        assertEquals(1, page.items().size());
        assertTrue(page.hasMore());
        assertEquals(9L, page.nextCursor());
        ArgumentCaptor<QueryWrapper<BalanceRecordDO>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(recordMapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("user_id"));
        assertTrue(sql.contains("record_id <"));
        assertTrue(sql.contains("LIMIT 2"));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue("RECHARGE"));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(1L));
    }

    @Test
    void shouldReturnEmptyPageWithoutNextCursor() {
        when(userMapper.selectBalanceById(1L)).thenReturn(user("70.00"));
        when(recordMapper.selectList(any())).thenReturn(List.of());
        assertFalse(service().listRecords(1L, null, null, 20).hasMore());
    }

    private UserWalletServiceImpl service() {
        return new UserWalletServiceImpl(userMapper, recordMapper, payPasswordService);
    }

    private RechargeRequest request(String amount) {
        RechargeRequest request = new RechargeRequest();
        request.setAmount(new BigDecimal(amount));
        request.setPayPassword("123456");
        return request;
    }

    private UserDO user(String balance) {
        UserDO user = new UserDO(); user.setUserId(1L); user.setStatus(1);
        user.setBalance(new BigDecimal(balance)); user.setPayPasswordHash("hashed");
        return user;
    }

    private BalanceRecordDO record(Long id, String amount) {
        BalanceRecordDO record = new BalanceRecordDO(); record.setRecordId(id); record.setChangeType("RECHARGE");
        record.setUserId(1L); record.setAmount(new BigDecimal(amount)); record.setBalanceAfter(new BigDecimal("70.00"));
        return record;
    }
}
