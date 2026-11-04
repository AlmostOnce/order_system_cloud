package com.hbue.ordering.order.service;
import com.hbue.ordering.order.mapper.OrderMapper;
import com.hbue.ordering.order.model.OrderDO;
import com.hbue.ordering.order.service.impl.OrderTimeoutServiceImpl;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OrderTimeoutServiceTest {
    @Test void onlyExpiredPendingOrderCanEnterExistingCancellationTransaction() {
        var mapper=mock(OrderMapper.class);var orders=mock(OrderService.class);
        var service=new OrderTimeoutServiceImpl(mapper,orders);
        var order=new OrderDO();order.setOrderId(1L);order.setUserId(9L);order.setStatus("待支付");
        order.setExpiresAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusMinutes(1));
        when(mapper.selectById(1L)).thenReturn(order);
        assertThrows(IllegalStateException.class,()->service.expire(1L));verifyNoInteractions(orders);
        order.setExpiresAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusSeconds(1));
        service.expire(1L);verify(orders).cancelExpiredOrder(1L);
        order.setStatus("已支付");service.expire(1L);verifyNoMoreInteractions(orders);
        order.setStatus("已取消");service.expire(1L);verifyNoMoreInteractions(orders);
    }
}
