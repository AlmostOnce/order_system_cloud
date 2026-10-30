package com.hbue.ordering.order.service;

import com.hbue.ordering.order.service.impl.OrderWindowNameServiceImpl;
import com.hbue.ordering.order.vo.OrderDetailVO;
import com.hbue.ordering.product.api.client.WindowInternalClient;
import com.hbue.ordering.common.response.ApiResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderWindowNameServiceTest {
    @Test void duplicateWindowsUseOneBatchAndPreserveOrderData() {
        var client = mock(WindowInternalClient.class);
        when(client.getWindowNames(List.of(2L), "internal")).thenReturn(ApiResponse.success(Map.of(2L, "川味小厨")));
        var result = new OrderWindowNameServiceImpl(client, "internal").enrich(List.of(
                OrderDetailVO.builder().orderId(7L).windowId(2L).status("已完成").build(),
                OrderDetailVO.builder().orderId(8L).windowId(2L).status("待支付").build()));
        assertEquals("川味小厨", result.getFirst().getWindowName());
        assertEquals("川味小厨", result.getLast().getWindowName());
        assertEquals("已完成", result.getFirst().getStatus());
        verify(client, times(1)).getWindowNames(List.of(2L), "internal");
    }
    @Test void nameFailureDoesNotHideOrder() {
        var client = mock(WindowInternalClient.class);
        when(client.getWindowNames(List.of(2L), "internal")).thenThrow(new IllegalStateException());
        var result = new OrderWindowNameServiceImpl(client, "internal").enrich(List.of(
                OrderDetailVO.builder().orderId(7L).windowId(2L).build()));
        assertEquals(7L, result.getFirst().getOrderId());
        assertNull(result.getFirst().getWindowName());
    }
}
