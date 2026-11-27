package com.hbue.ordering.stock.messaging;

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
 * 校验库存事件 Outbox 发布器的租约门控：多实例同时扫描到同一行时只能有一方发送。
 */
class StockEventPublisherTest {

    private final StockEventOutboxMapper outbox = mock(StockEventOutboxMapper.class);
    private final StreamBridge stream = mock(StreamBridge.class);
    private final StockEventPublisher publisher = new StockEventPublisher(outbox, stream);

    /**
     * 构造一条字段完整的库存事件行，事件号由业务键重算，确保能通过 valid() 校验。
     */
    private StockEventOutboxRow row(String bizRef, long stockId) {
        var row = new StockEventOutboxRow();
        row.setBizType("SALE");
        row.setBizRef(bizRef);
        row.setStockId(stockId);
        row.setWindowId(1L);
        row.setProductId(2L);
        row.setAvailableQty(10);
        row.setStockVersion(1);
        row.setEventId(StockChangedEvent.eventId("SALE", bizRef, stockId));
        return row;
    }

    /**
     * 只有领取到租约的实例可以发送并标记已发布，领取失败的行必须跳过且不退避。
     */
    @Test
    void onlyLeaseWinnerPublishesEvent() {
        var first = row("ORD-1", 3L);
        var second = row("ORD-2", 4L);
        when(outbox.pending()).thenReturn(List.of(first, second));
        when(outbox.lease(first.getEventId(), 30)).thenReturn(1);
        when(outbox.lease(second.getEventId(), 30)).thenReturn(0);
        when(stream.send(eq("stockChanged-out-0"), any())).thenReturn(true);

        publisher.publishPending();

        verify(stream, times(1)).send(eq("stockChanged-out-0"), any());
        verify(outbox).markPublished(first.getEventId());
        verify(outbox, never()).markPublished(second.getEventId());
        verify(outbox, never()).retryLater(anyString(), anyString());
    }
}
