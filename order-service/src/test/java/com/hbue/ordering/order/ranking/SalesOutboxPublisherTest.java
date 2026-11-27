package com.hbue.ordering.order.ranking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.order.mapper.SalesOutboxMapper;
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
 * 校验销量 Outbox 发布器的租约门控：多实例同时扫描到同一行时只能有一方真正发送。
 */
class SalesOutboxPublisherTest {

    private final SalesOutboxMapper outbox = mock(SalesOutboxMapper.class);
    private final ObjectMapper json = mock(ObjectMapper.class);
    private final StreamBridge stream = mock(StreamBridge.class);
    private final SalesOutboxPublisher publisher = new SalesOutboxPublisher(outbox, json, stream);

    /**
     * 构造一条待发 outbox 行，载荷内容由 ObjectMapper 的 mock 决定。
     */
    private SalesOutboxRow row(String eventId, long orderId) {
        var row = new SalesOutboxRow();
        row.setEventId(eventId);
        row.setOrderId(orderId);
        row.setPayload("{}");
        return row;
    }

    /**
     * 两行同时待发、只有第一行领取到租约时，只允许发送并标记第一行，第二行不应被投递。
     *
     * <p>模拟两个实例在同一轮扫描到相同记录：租约失败的实例必须跳过，否则销量统计会被重复累加。</p>
     */
    @Test
    void onlyLeaseWinnerPublishesEvent() throws Exception {
        var first = row("order-paid-sales:9", 9L);
        var second = row("order-paid-sales:10", 10L);
        when(outbox.pending()).thenReturn(List.of(first, second));
        when(json.readValue("{}", OrderPaidSalesEvent.class))
                .thenReturn(OrderPaidSalesEvent.of(9L), OrderPaidSalesEvent.of(10L));
        when(outbox.lease("order-paid-sales:9", 30)).thenReturn(1);
        when(outbox.lease("order-paid-sales:10", 30)).thenReturn(0);
        when(stream.send(eq("salesPaid-out-0"), any())).thenReturn(true);
        when(outbox.markPublished("order-paid-sales:9")).thenReturn(1);

        publisher.publishPending();

        verify(stream, times(1)).send(eq("salesPaid-out-0"), any());
        verify(outbox).markPublished("order-paid-sales:9");
        verify(outbox, never()).markPublished("order-paid-sales:10");
        verify(outbox, never()).retryLater(anyString(), anyString());
    }
}
