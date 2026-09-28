package com.hbue.ordering.order.service;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.order.mapper.OrderItemMapper;
import com.hbue.ordering.order.mapper.OrderMapper;
import com.hbue.ordering.order.model.OrderDO;
import com.hbue.ordering.order.model.OrderItemDO;
import com.hbue.ordering.order.service.impl.OrderPersistenceServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
                orderItemMapper
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
                orderItemMapper
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

    @Test
    void shouldKeepTransactionBoundaryOnImplementationMethod() throws NoSuchMethodException {
        assertTrue(OrderPersistenceServiceImpl.class
                .getMethod("saveOrderWithItems", OrderDO.class, List.class)
                .isAnnotationPresent(Transactional.class));
    }
}
