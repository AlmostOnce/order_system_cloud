package com.hbue.ordering.notification.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.notification.mapper.NotificationMapper;
import com.hbue.ordering.notification.service.impl.NotificationServiceImpl;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationServiceTest {
    private static final String ID = "a1111111-1111-4111-8111-111111111111";
    @Test
    void rejectsAnotherUsersCursorAndDoesNotQueryPage() {
        var repository = mock(NotificationMapper.class);
        var service = new NotificationServiceImpl(repository);
        assertThrows(BusinessException.class, () -> service.page(7, ID, 20));
        verify(repository, never()).page(anyLong(), any(), anyInt());
    }
    @Test
    void markReadIsScopedToTheAuthenticatedUser() {
        var repository = mock(NotificationMapper.class);
        when(repository.existsForUser(7, ID)).thenReturn(true);
        var service = new NotificationServiceImpl(repository);
        service.markRead(7, ID);
        verify(repository).markRead(7, ID);
        assertThrows(BusinessException.class, () -> service.markRead(8, ID));
        verify(repository, never()).markRead(8, ID);
    }
    @Test
    void emptyPageContainsNoCursor() {
        var repository = mock(NotificationMapper.class);
        when(repository.page(7, null, 21)).thenReturn(List.of());
        var page = new NotificationServiceImpl(repository).page(7, null, 20);
        assertTrue(page.items().isEmpty());
        assertFalse(page.hasMore());
        assertNull(page.nextCursor());
    }
}
