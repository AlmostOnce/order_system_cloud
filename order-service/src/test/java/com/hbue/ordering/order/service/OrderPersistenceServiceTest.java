package com.hbue.ordering.order.service;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.order.mapper.OrderItemMapper;
import com.hbue.ordering.order.mapper.OrderMapper;
import com.hbue.ordering.order.model.OrderDO;
import com.hbue.ordering.order.model.OrderItemDO;
import com.hbue.ordering.order.service.impl.OrderPersistenceServiceImpl;
import com.hbue.ordering.order.ranking.SalesOutboxWriter;
import com.hbue.ordering.order.notification.OrderNotificationOutboxWriter;
import com.hbue.ordering.order.notification.OrderNotificationEvent;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPersistenceServiceTest {

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderItemMapper orderItemMapper;

    @Mock
    private SalesOutboxWriter salesOutbox;
    @Mock
    private OrderNotificationOutboxWriter notificationOutbox;

    @Test
    void shouldSaveOrderBeforeAllItemsAndAssignOrderId() {
        when(orderMapper.insert(any(OrderDO.class))).thenAnswer(invocation -> {
            OrderDO order = invocation.getArgument(0);
            order.setOrderId(81L);
            return 1;
        });
        when(orderItemMapper.insert(any(OrderItemDO.class))).thenReturn(1);
        OrderPersistenceService persistenceService = new OrderPersistenceServiceImpl(
                orderMapper,
                orderItemMapper, salesOutbox
        );
        List<OrderItemDO> items = List.of(new OrderItemDO(), new OrderItemDO());

        OrderDO savedOrder = persistenceService.saveOrderWithItems(
                new OrderDO(),
                items
        );

        assertEquals(81L, savedOrder.getOrderId());
        assertEquals(81L, items.getFirst().getOrderId());
        assertEquals(81L, items.getLast().getOrderId());
        ArgumentCaptor<OrderItemDO> itemCaptor =
                ArgumentCaptor.forClass(OrderItemDO.class);
        verify(orderItemMapper, times(2)).insert(itemCaptor.capture());
        assertEquals(81L, itemCaptor.getAllValues().getFirst().getOrderId());
    }

    @Test
    void shouldStopWhenOrderInsertFails() {
        when(orderMapper.insert(any(OrderDO.class))).thenReturn(0);
        OrderPersistenceService persistenceService = new OrderPersistenceServiceImpl(
                orderMapper,
                orderItemMapper, salesOutbox
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> persistenceService.saveOrderWithItems(
                        new OrderDO(),
                        List.of(new OrderItemDO())
                )
        );

        assertEquals(CommonErrorCode.INTERNAL_ERROR.code(), exception.getErrorCode().code());
        verify(orderItemMapper, never()).insert(any(OrderItemDO.class));
    }

    /**
     * 订单状态更新必须按订单 ID、旧状态和版本号执行条件更新。
     */
    @Test
    void shouldUpdateStatusOnlyWhenCurrentStatusAndVersionMatch() {
        when(orderMapper.updateStatusIfVersionMatches(
                81L,
                "待支付",
                3,
                "已取消"
        )).thenReturn(1);
        OrderPersistenceService persistenceService = new OrderPersistenceServiceImpl(
                orderMapper,
                orderItemMapper, salesOutbox
        );

        boolean updated = persistenceService.updateStatusIfUnchanged(
                81L,
                "待支付",
                3,
                "已取消"
        );

        assertTrue(updated);
        verify(orderMapper).updateStatusIfVersionMatches(
                81L,
                "待支付",
                3,
                "已取消"
        );
    }

    /**
     * 条件更新未命中时必须报告失败，供业务层返回并发冲突。
     */
    @Test
    void shouldReportStatusUpdateConflictWhenNoRowMatches() {
        when(orderMapper.updateStatusIfVersionMatches(
                81L,
                "待支付",
                3,
                "已取消"
        )).thenReturn(0);
        OrderPersistenceService persistenceService = new OrderPersistenceServiceImpl(
                orderMapper,
                orderItemMapper, salesOutbox
        );

        boolean updated = persistenceService.updateStatusIfUnchanged(
                81L,
                "待支付",
                3,
                "已取消"
        );

        assertFalse(updated);
    }

    @Test
    void shouldKeepTransactionBoundaryOnImplementationMethod() throws NoSuchMethodException {
        assertTrue(OrderPersistenceServiceImpl.class
                .getMethod("saveOrderWithItems", OrderDO.class, List.class)
                .isAnnotationPresent(Transactional.class));
        assertTrue(OrderPersistenceServiceImpl.class
                .getMethod("updateStatusIfUnchanged", Long.class, String.class, Integer.class, String.class)
                .isAnnotationPresent(Transactional.class));
    }

    @Test
    void onlyWinningPaidTransitionWritesOutbox() {
        var persistence = new OrderPersistenceServiceImpl(orderMapper, orderItemMapper, salesOutbox);
        when(orderMapper.updateStatusIfVersionMatches(81L, "待支付", 3, "已支付")).thenReturn(1, 0);
        assertTrue(persistence.updateStatusIfUnchanged(81L, "待支付", 3, "已支付"));
        assertFalse(persistence.updateStatusIfUnchanged(81L, "待支付", 3, "已支付"));
        verify(salesOutbox, times(1)).recordPaid(81L);
    }

    @Test
    void outboxFailurePropagatesSoPaymentStateTransactionRollsBack() {
        var persistence = new OrderPersistenceServiceImpl(orderMapper, orderItemMapper, salesOutbox);
        when(orderMapper.updateStatusIfVersionMatches(81L, "待支付", 3, "已支付")).thenReturn(1);
        org.mockito.Mockito.doThrow(new IllegalStateException("write failed")).when(salesOutbox).recordPaid(81L);
        assertThrows(IllegalStateException.class, () -> persistence.updateStatusIfUnchanged(81L, "待支付", 3, "已支付"));
    }

    @Test
    void writesNotificationOnlyForWinningNonCancelTransition() {
        var persistence = new OrderPersistenceServiceImpl(orderMapper, orderItemMapper, salesOutbox);
        ReflectionTestUtils.setField(persistence, "notificationOutbox", notificationOutbox);
        ReflectionTestUtils.setField(persistence, "notificationsEnabled", true);
        var current = new OrderDO(); current.setOrderId(81L); current.setUserId(7L); current.setVersion(4); current.setStatus("制作中");
        when(orderMapper.updateStatusIfVersionMatches(81L, "已支付", 3, "制作中")).thenReturn(1, 0);
        when(orderMapper.selectById(81L)).thenReturn(current);
        assertTrue(persistence.updateStatusIfUnchanged(81L, "已支付", 3, "制作中"));
        assertFalse(persistence.updateStatusIfUnchanged(81L, "已支付", 3, "制作中"));
        verify(notificationOutbox, times(1)).record(OrderNotificationEvent.of(81L, 4, 7L, "制作中"));
    }

    @Test
    void pickupCompletionConsumesCodeAndNotifiesOnlyWinningTransition() {
        var persistence = new OrderPersistenceServiceImpl(orderMapper, orderItemMapper, salesOutbox);
        ReflectionTestUtils.setField(persistence, "notificationOutbox", notificationOutbox);
        ReflectionTestUtils.setField(persistence, "notificationsEnabled", true);
        var completed = new OrderDO(); completed.setOrderId(81L); completed.setUserId(7L);
        completed.setVersion(5); completed.setStatus("已完成");
        when(orderMapper.completeWithPickupCode(81L, 4, "012345")).thenReturn(1, 0);
        when(orderMapper.selectById(81L)).thenReturn(completed);
        assertTrue(persistence.completeWithPickupCode(81L, 4, "012345"));
        assertFalse(persistence.completeWithPickupCode(81L, 4, "012345"));
        verify(notificationOutbox, times(1)).record(OrderNotificationEvent.of(81L, 5, 7L, "已完成"));
    }

    @Test
    void sharedOrderResponsesNeverExposePickupCode() throws Exception {
        var order = new OrderDO(); order.setOrderId(81L); order.setPickupCode("012345");
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        assertFalse(json.writeValueAsString(order).contains("012345"));
        assertFalse(json.writeValueAsString(com.hbue.ordering.order.vo.OrderDetailVO.from(order)).contains("012345"));
        assertFalse(order.toString().contains("012345"));
    }
    @Test
    void pickupCodeCollisionRetriesWithoutReplacingAnotherOrdersCode() {
        var order = new OrderDO(); order.setOrderId(81L); order.setUserId(7L);
        order.setStatus("待取货"); order.setPickupCode("012345");
        when(orderMapper.assignPickupCodeIfMissing(eq(81L), eq(7L), anyString()))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("collision")).thenReturn(1);
        when(orderMapper.selectById(81L)).thenReturn(order);
        assertEquals("012345", persistence.ensurePickupCode(81L, 7L));
        verify(orderMapper, times(2)).assignPickupCodeIfMissing(eq(81L), eq(7L), anyString());
    }

}
