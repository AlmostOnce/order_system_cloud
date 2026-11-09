package com.hbue.ordering.order.controller;

import com.hbue.ordering.common.web.security.InternalScopeTokenVerifier;
import com.hbue.ordering.order.model.OrderDO;
import com.hbue.ordering.order.service.OrderService;
import com.hbue.ordering.order.service.OrderWindowNameService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 完成后售后会更新订单，聊天期限只能以首次核销时间计算，不能随updatedAt续期。 */
class OrderChatContextTest {
    @Test void laterOrderUpdateDoesNotExtendChatWindow() {
        var orders = mock(OrderService.class);
        var names = mock(OrderWindowNameService.class);
        var tokens = mock(InternalScopeTokenVerifier.class);
        var completed = LocalDateTime.of(2026, 11, 6, 12, 0);
        var order = new OrderDO();
        order.setOrderId(1L);
        order.setOrderNo("CHAT-1");
        order.setUserId(7L);
        order.setWindowId(3L);
        order.setStatus("已完成");
        order.setTotalAmount(BigDecimal.TEN);
        order.setCreatedAt(completed.minusHours(1));
        order.setCompletedAt(completed);
        order.setUpdatedAt(completed.plusDays(2));
        when(orders.getById(1L)).thenReturn(order);
        when(names.enrich(anyList())).thenAnswer(call -> call.getArgument(0));
        var result = new OrderChatInternalController(orders, names, tokens).context(1L, "internal");
        assertEquals(completed, result.getData().completedAt());
        verify(tokens).verify("internal");
    }
}
