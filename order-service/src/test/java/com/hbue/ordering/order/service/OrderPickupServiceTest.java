package com.hbue.ordering.order.service;

import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.core.scope.DataScopeService;
import com.hbue.ordering.common.redis.RedisOperationsService;
import com.hbue.ordering.common.web.audit.ManagementAuditLogger;
import com.hbue.ordering.order.mapper.OrderMapper;
import com.hbue.ordering.order.model.OrderDO;
import com.hbue.ordering.order.service.impl.OrderPickupServiceImpl;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderPickupServiceTest {
    private final OrderMapper orders = mock(OrderMapper.class);
    private final OrderPersistenceService persistence = mock(OrderPersistenceService.class);
    private final DataScopeService scopes = mock(DataScopeService.class);
    private final RedisOperationsService redis = mock(RedisOperationsService.class);
    private final ManagementAuditLogger audit = mock(ManagementAuditLogger.class);
    private final OrderPickupServiceImpl service = new OrderPickupServiceImpl(orders, persistence, scopes, redis, audit);
    private final OrderDO order = new OrderDO();

    OrderPickupServiceTest() {
        order.setOrderId(18L); order.setOrderNo("ORD123"); order.setUserId(7L); order.setWindowId(2L);
        order.setStatus("待取货"); order.setVersion(4); order.setPickupCode("012345");
        when(orders.selectByActivePickupCode("012345")).thenReturn(order);
        when(orders.selectById(18L)).thenReturn(order);
        when(scopes.canAccessWindow(9L, 2L)).thenReturn(true);
        when(redis.increment("order:pickup:operator-attempts:9", Duration.ofMinutes(1))).thenReturn(1L);
    }

    @Test void onlyOrderOwnerCanRetrieveCode() {
        when(persistence.ensurePickupCode(18L, 7L)).thenReturn("012345");
        assertEquals("012345", service.getOwnPickupCode(7L, 18L));
        assertThrows(BusinessException.class, () -> service.getOwnPickupCode(9L, 18L));
        verify(persistence, never()).ensurePickupCode(18L, 9L);
    }

    @Test void correctCodeCompletesOnlyScopedWaitingOrder() {
        when(persistence.completeWithPickupCode(18L, 4, "012345")).thenReturn(true);
        assertEquals("已完成", service.verifyPickup(9L, "012345").getStatus());
        verify(persistence).completeWithPickupCode(18L, 4, "012345");
    }

    @Test void wrongCodeNeverChangesOrder() {
        assertThrows(BusinessException.class, () -> service.verifyPickup(9L, "999999"));
        verifyNoInteractions(persistence);
    }

    @Test void outOfScopeCannotVerifyButStillConsumesAttemptBudget() {
        when(scopes.canAccessWindow(9L, 2L)).thenReturn(false);
        assertThrows(BusinessException.class, () -> service.verifyPickup(9L, "012345"));
        verifyNoInteractions(persistence);
        verify(redis).increment("order:pickup:operator-attempts:9", Duration.ofMinutes(1));
    }

    @Test void preparingAndCompletedOrdersCannotBeVerified() {
        order.setStatus("制作中");
        assertThrows(BusinessException.class, () -> service.verifyPickup(9L, "012345"));
        order.setStatus("已完成");
        assertThrows(BusinessException.class, () -> service.verifyPickup(9L, "012345"));
        verifyNoInteractions(persistence);
        verify(redis, times(2)).increment("order:pickup:operator-attempts:9", Duration.ofMinutes(1));
    }

    @Test void rateLimitAndConcurrentUpdateAreRejected() {
        when(redis.increment(anyString(), any(Duration.class))).thenReturn(31L);
        assertThrows(BusinessException.class, () -> service.verifyPickup(9L, "012345"));
        verifyNoInteractions(persistence);
        when(redis.increment(anyString(), any(Duration.class))).thenReturn(1L);
        when(persistence.completeWithPickupCode(18L, 4, "012345")).thenReturn(false);
        assertThrows(BusinessException.class, () -> service.verifyPickup(9L, "012345"));
    }
}
