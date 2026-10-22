package com.hbue.ordering.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.order.mapper.SalesOutboxMapper;
import com.hbue.ordering.order.ranking.*;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.support.ErrorMessage;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SalesRankingMessagingTest {
    @Test
    void failedSendDoesNotMarkOutboxPublished() throws Exception {
        var mapper = mock(SalesOutboxMapper.class);
        var stream = mock(StreamBridge.class);
        var json = new ObjectMapper();
        var row = new SalesOutboxRow();
        row.setOrderId(7L); row.setEventId("order-paid-sales:7");
        row.setPayload(json.writeValueAsString(OrderPaidSalesEvent.of(7L)));
        when(mapper.pending()).thenReturn(List.of(row));
        when(stream.send(eq("salesPaid-out-0"), any())).thenReturn(false);
        new SalesRankingMessaging(mapper, json, stream, mock(SalesRankingService.class)).publishPending();
        verify(mapper, never()).markPublished(anyString());
        verify(mapper).retryLater(eq(row.getEventId()), anyString());
    }

    @Test
    void successfulSynchronousSendMarksPublished() throws Exception {
        var mapper = mock(SalesOutboxMapper.class);
        var stream = mock(StreamBridge.class);
        var json = new ObjectMapper();
        var row = new SalesOutboxRow();
        row.setOrderId(7L); row.setEventId("order-paid-sales:7");
        row.setPayload(json.writeValueAsString(OrderPaidSalesEvent.of(7L)));
        when(mapper.pending()).thenReturn(List.of(row));
        when(stream.send(eq("salesPaid-out-0"), any())).thenReturn(true);
        new SalesRankingMessaging(mapper, json, stream, mock(SalesRankingService.class)).publishPending();
        verify(mapper).markPublished(row.getEventId());
        verify(mapper, never()).retryLater(anyString(), anyString());
    }

    @Test
    void failedConsumerAndErrorHandlerPropagateForBrokerRetry() {
        var rankings = mock(SalesRankingService.class);
        var config = new SalesRankingMessaging(mock(SalesOutboxMapper.class), new ObjectMapper(), mock(StreamBridge.class), rankings);
        doThrow(new IllegalStateException("Redis unavailable")).when(rankings).acceptPaidOrder(7L);
        assertThrows(IllegalStateException.class, () -> config.salesRankingPaid().accept(OrderPaidSalesEvent.of(7L)));
        assertThrows(IllegalArgumentException.class, () -> config.salesRankingPaid().accept(new OrderPaidSalesEvent(1, "wrong-id", 7L)));
        assertThrows(IllegalStateException.class, () -> config.salesRankingRetryError().accept(new ErrorMessage(new IllegalStateException("fail"))));
    }
}
