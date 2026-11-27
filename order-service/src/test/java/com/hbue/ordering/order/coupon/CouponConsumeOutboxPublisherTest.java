package com.hbue.ordering.order.coupon;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.order.mapper.CouponConsumeOutboxMapper;
import com.hbue.ordering.order.model.CouponConsumeOutboxRow;
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
 * 校验优惠券核销 Outbox 发布器的租约门控，确保多实例不会重复投递同一核销事件。
 */
class CouponConsumeOutboxPublisherTest {

    private final CouponConsumeOutboxMapper outbox = mock(CouponConsumeOutboxMapper.class);
    private final ObjectMapper json = mock(ObjectMapper.class);
    private final StreamBridge stream = mock(StreamBridge.class);
    private final CouponConsumeOutboxPublisher publisher = new CouponConsumeOutboxPublisher(outbox, json, stream);

    /**
     * 构造一条待发 outbox 行，字段与事件快照保持一致。
     */
    private CouponConsumeOutboxRow row(String orderNo, long userCouponId) {
        var row = new CouponConsumeOutboxRow();
        row.setEventId("order-coupon-paid:" + orderNo);
        row.setOrderNo(orderNo);
        row.setUserCouponId(userCouponId);
        row.setPayload("{}");
        return row;
    }

    /**
     * 只有领取到租约的实例可以发送；领取失败的记录既不发送也不进入失败退避。
     */
    @Test
    void onlyLeaseWinnerPublishesEvent() throws Exception {
        var first = row("ORDER-1", 5L);
        var second = row("ORDER-2", 6L);
        when(outbox.pending()).thenReturn(List.of(first, second));
        when(json.readValue("{}", OrderCouponConsumeEvent.class))
                .thenReturn(OrderCouponConsumeEvent.of("ORDER-1", 5L), OrderCouponConsumeEvent.of("ORDER-2", 6L));
        when(outbox.lease("order-coupon-paid:ORDER-1", 30)).thenReturn(1);
        when(outbox.lease("order-coupon-paid:ORDER-2", 30)).thenReturn(0);
        when(stream.send(eq("couponPaid-out-0"), any())).thenReturn(true);
        when(outbox.markPublished("order-coupon-paid:ORDER-1")).thenReturn(1);

        publisher.publishPending();

        verify(stream, times(1)).send(eq("couponPaid-out-0"), any());
        verify(outbox).markPublished("order-coupon-paid:ORDER-1");
        verify(outbox, never()).markPublished("order-coupon-paid:ORDER-2");
        verify(outbox, never()).retryLater(anyString(), anyString());
    }
}
