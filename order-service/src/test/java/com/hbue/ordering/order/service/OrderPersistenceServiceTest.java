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
import java.time.LocalDateTime;

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

    /** 支付时间使用支付服务确认的固定值，不从订单更新时间或当前时刻推断。 */
    private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 11, 13, 9, 30);

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderItemMapper orderItemMapper;

    @Mock
    private SalesOutboxWriter salesOutbox;
    @Mock
    private OrderNotificationOutboxWriter notificationOutbox;

    @Mock
    private OrderTimeoutOutboxService timeoutOutbox;

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
                orderItemMapper, salesOutbox, timeoutOutbox
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
                orderItemMapper, salesOutbox, timeoutOutbox
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
                orderItemMapper, salesOutbox, timeoutOutbox
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
                orderItemMapper, salesOutbox, timeoutOutbox
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
        var persistence = new OrderPersistenceServiceImpl(orderMapper, orderItemMapper, salesOutbox, timeoutOutbox);
        when(orderMapper.markPaidIfVersionMatches(81L, 3, PAID_AT)).thenReturn(1, 0);
        assertTrue(persistence.markPaidIfUnchanged(81L, 3, PAID_AT));
        assertFalse(persistence.markPaidIfUnchanged(81L, 3, PAID_AT));
        verify(salesOutbox, times(1)).recordPaid(81L, PAID_AT);
    }

    @Test
    void outboxFailurePropagatesSoPaymentStateTransactionRollsBack() {
        var persistence = new OrderPersistenceServiceImpl(orderMapper, orderItemMapper, salesOutbox, timeoutOutbox);
        when(orderMapper.markPaidIfVersionMatches(81L, 3, PAID_AT)).thenReturn(1);
        org.mockito.Mockito.doThrow(new IllegalStateException("write failed"))
                .when(salesOutbox).recordPaid(81L, PAID_AT);
        assertThrows(IllegalStateException.class, () -> persistence.markPaidIfUnchanged(81L, 3, PAID_AT));
    }

    @Test
    void writesNotificationOnlyForWinningNonCancelTransition() {
        var persistence = new OrderPersistenceServiceImpl(orderMapper, orderItemMapper, salesOutbox, timeoutOutbox);
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
        var persistence = new OrderPersistenceServiceImpl(orderMapper, orderItemMapper, salesOutbox, timeoutOutbox);
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
        // 详情契约需要真实创建时间才能计算支付期限，安全字段测试也必须提供完整最小订单。
        var order = new OrderDO(); order.setOrderId(81L); order.setPickupCode("012345");
        order.setCreatedAt(java.time.LocalDateTime.of(2026,11,6,10,0));
        // 与Spring Boot实际序列化保持一致，注册Java时间模块。
        var json = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        assertFalse(json.writeValueAsString(order).contains("012345"));
        assertFalse(json.writeValueAsString(com.hbue.ordering.order.vo.OrderDetailVO.from(order)).contains("012345"));
        assertFalse(order.toString().contains("012345"));
    }
    @Test
    void pickupCodeCollisionRetriesWithoutReplacingAnotherOrdersCode() {
        var persistence = new OrderPersistenceServiceImpl(orderMapper, orderItemMapper, salesOutbox, timeoutOutbox);
        var order = new OrderDO(); order.setOrderId(81L); order.setUserId(7L);
        order.setStatus("待取货"); order.setPickupCode("012345");
        when(orderMapper.assignPickupCodeIfMissing(eq(81L), eq(7L), anyString()))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("collision")).thenReturn(1);
        when(orderMapper.selectById(81L)).thenReturn(order);
        var item = new OrderItemDO(); item.setQuantity(1); item.setRefundApprovedQuantity(0);
        when(orderItemMapper.selectByOrderId(81L)).thenReturn(List.of(item));
        assertEquals("012345", persistence.ensurePickupCode(81L, 7L));
        verify(orderMapper, times(2)).assignPickupCodeIfMissing(eq(81L), eq(7L), anyString());
    }

    /** 全部份数获批退款时，即使旧取餐码仍未清理也不能继续返回。 */
    @Test void approvedRefundHidesPreviouslyIssuedPickupCode() {
        var persistence = new OrderPersistenceServiceImpl(orderMapper, orderItemMapper, salesOutbox, timeoutOutbox);
        var order = new OrderDO(); order.setOrderId(81L); order.setUserId(7L);
        order.setStatus("待取货"); order.setPickupCode("012345");
        var item = new OrderItemDO(); item.setQuantity(1); item.setRefundApprovedQuantity(1);
        when(orderMapper.selectById(81L)).thenReturn(order);
        when(orderItemMapper.selectByOrderId(81L)).thenReturn(List.of(item));
        assertThrows(com.hbue.ordering.common.core.exception.BusinessException.class,
                () -> persistence.ensurePickupCode(81L,7L));
    }

}
