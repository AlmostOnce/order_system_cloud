package com.hbue.ordering.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.order.api.dto.CouponConsumeConfirmationRequest;
import com.hbue.ordering.order.mapper.CouponConsumeOutboxMapper;
import com.hbue.ordering.order.mapper.OrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 支付券核销 Outbox 回执的轻量断言，不依赖数据库、Seata 或 RocketMQ。 */
@ExtendWith(MockitoExtension.class)
class CouponConsumeOutboxWriterTest {

    @Mock private CouponConsumeOutboxMapper outbox;
    @Mock private OrderMapper orders;
    @Mock private ObjectMapper json;

    private CouponConsumeOutboxWriter writer;

    /** 用 mock 持久化边界创建回执处理器。 */
    @BeforeEach
    void setUp() {
        writer = new CouponConsumeOutboxWriter(outbox, orders, json);
    }

    /** 同一事件的重复消费回执按已确认快照幂等成功，不会要求重新改变状态。 */
    @Test
    void duplicateConfirmationIsIdempotentForTheSameEventTuple() {
        var request = new CouponConsumeConfirmationRequest("event-1", "ORDER-1", 8L);
        when(outbox.confirm("event-1", "ORDER-1", 8L)).thenReturn(0);
        when(outbox.isConfirmed("event-1", "ORDER-1", 8L)).thenReturn(1);

        writer.confirm(request);

        verify(outbox).confirm("event-1", "ORDER-1", 8L);
        verify(outbox).isConfirmed("event-1", "ORDER-1", 8L);
    }

    /** 不同订单或券 ID 不能借用已有事件的回执来关闭 Outbox。 */
    @Test
    void mismatchedConfirmationCannotCloseOutbox() {
        var request = new CouponConsumeConfirmationRequest("event-1", "ORDER-OTHER", 8L);
        when(outbox.confirm("event-1", "ORDER-OTHER", 8L)).thenReturn(0);
        when(outbox.isConfirmed("event-1", "ORDER-OTHER", 8L)).thenReturn(0);

        assertThrows(BusinessException.class, () -> writer.confirm(request));
    }

    /** 无效回执在访问持久化前即拒绝。 */
    @Test
    void invalidConfirmationDoesNotQueryOutbox() {
        assertThrows(BusinessException.class,
                () -> writer.confirm(new CouponConsumeConfirmationRequest("", "ORDER-1", 8L)));

        verify(outbox, never()).confirm("", "ORDER-1", 8L);
    }
}
