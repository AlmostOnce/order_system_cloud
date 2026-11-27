package com.hbue.ordering.order.messaging;

import com.hbue.ordering.order.dto.OrderTimeoutEvent;
import com.hbue.ordering.order.service.OrderTimeoutOutboxService;
import com.hbue.ordering.order.service.OrderTimeoutService;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.stream.function.StreamBridge;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 校验订单超时 Outbox 发布任务的租约门控：同一事件在多实例下只投递一次。
 */
class OrderTimeoutMessagingPublishTest {

    private final OrderTimeoutOutboxService outbox = mock(OrderTimeoutOutboxService.class);
    private final OrderTimeoutService timeouts = mock(OrderTimeoutService.class);
    private final StreamBridge stream = mock(StreamBridge.class);
    private final OrderTimeoutMessaging messaging = new OrderTimeoutMessaging(outbox, timeouts, stream);

    /**
     * 两行同时待发、只有第一行领取到租约时，只发送第一行并标记 sent，第二行既不发送也不标记失败。
     */
    @Test
    void onlyLeaseWinnerPublishesEvent() {
        var first = OrderTimeoutEvent.of(9L, System.currentTimeMillis() + 60_000);
        var second = OrderTimeoutEvent.of(10L, System.currentTimeMillis() + 60_000);
        when(outbox.pending()).thenReturn(List.of(first, second));
        when(outbox.lease(first.eventId(), 30)).thenReturn(true);
        when(outbox.lease(second.eventId(), 30)).thenReturn(false);
        when(stream.send(eq("orderTimeout-out-0"), any())).thenReturn(true);

        messaging.publish();

        verify(stream, times(1)).send(eq("orderTimeout-out-0"), any());
        verify(outbox).sent(first.eventId());
        verify(outbox, never()).sent(second.eventId());
        verify(outbox, never()).failed(anyString(), anyString());
    }
}
