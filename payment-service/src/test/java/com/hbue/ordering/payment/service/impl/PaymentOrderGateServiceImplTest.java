package com.hbue.ordering.payment.service.impl;

import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.payment.mapper.OrderPaymentGateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 支付入口闸门的补偿语义测试。
 *
 * <p>用真实 {@link org.springframework.transaction.support.TransactionTemplate} 搭配桩化的事务管理器，
 * 只验证"补行 → 行锁 → 更新"的调用顺序与关闭态拒绝行为，不启动 Spring 上下文、不连接数据库。</p>
 */
class PaymentOrderGateServiceImplTest {

    /**
     * 订单主键，用于校验闸门调用参数。
     */
    private static final Long ORDER_ID = 9L;

    /**
     * 闸门状态数据访问桩。
     */
    private final OrderPaymentGateMapper mapper = mock(OrderPaymentGateMapper.class);

    /**
     * 事务管理器桩，返回一个可提交/回滚的空事务状态。
     */
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);

    /**
     * 被测闸门服务。
     */
    private PaymentOrderGateServiceImpl gate;

    /**
     * 每个用例重建闸门服务，并让事务模板拿到可用的空事务状态。
     */
    @BeforeEach
    void setUp() {
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        gate = new PaymentOrderGateServiceImpl(mapper, manager);
    }

    /**
     * 闸门处于关闭态时，支付相关动作必须被拒绝，不能进入记账逻辑。
     */
    @Test
    void closingGateRejectsAction() {
        when(mapper.lock(ORDER_ID)).thenReturn(1);

        assertThrows(BusinessException.class, () -> gate.whileOpen(ORDER_ID, () -> "action"));
    }

    /**
     * 撤销关闭意图必须在同一把行锁内完成补行与复位，顺序与关单保持一致。
     */
    @Test
    void reopenClearsClosingFlagUnderRowLock() {
        gate.reopen(ORDER_ID);

        verify(mapper).ensure(ORDER_ID);
        verify(mapper).lock(ORDER_ID);
        verify(mapper).reopen(ORDER_ID);
    }
}
