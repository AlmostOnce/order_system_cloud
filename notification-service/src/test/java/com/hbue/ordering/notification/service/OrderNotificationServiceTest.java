package com.hbue.ordering.notification.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.hbue.ordering.notification.messaging.OrderNotificationEvent;
import com.hbue.ordering.notification.repository.NotificationRepository;
import com.hbue.ordering.notification.service.impl.OrderNotificationServiceImpl;
import com.hbue.ordering.notification.websocket.NotificationSocketBus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class OrderNotificationServiceTest {
    private static final OrderNotificationEvent EVENT = new OrderNotificationEvent(1, "order-status:7:2", 7L, 2, 9L, "待取货");
    @Test
    void acceptsValidEventsAndRejectsInvalidOnes() {
        var repository = mock(NotificationRepository.class);
        var service = new OrderNotificationServiceImpl(repository, mock(NotificationSocketBus.class));
        TransactionSynchronizationManager.initSynchronization();
        try { service.accept(EVENT); } finally { TransactionSynchronizationManager.clearSynchronization(); }
        verify(repository).insertOrderEvent(anyString(), eq(9L), eq("order-status:7:2"), eq(7L), anyString());
        assertThrows(IllegalArgumentException.class, () -> service.accept(new OrderNotificationEvent(1, "wrong", 7L, 2, 9L, "待取货")));
    }
    @Test
    void onlyTreatsTheSameRecipientAndEventAsADuplicate() {
        var repository = mock(NotificationRepository.class);
        doThrow(new DuplicateKeyException("duplicate")).when(repository).insertOrderEvent(anyString(), anyLong(), anyString(), anyLong(), anyString());
        when(repository.existsEvent(9L, "order-status:7:2")).thenReturn(true, false);
        var service = new OrderNotificationServiceImpl(repository, mock(NotificationSocketBus.class));
        assertDoesNotThrow(() -> service.accept(EVENT));
        assertThrows(DuplicateKeyException.class, () -> service.accept(EVENT));
    }
}
