package com.hbue.ordering.order.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.order.mapper.OrderNotificationOutboxMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderNotificationOutboxWriter {
    private final OrderNotificationOutboxMapper outbox;
    private final ObjectMapper json;
    public OrderNotificationOutboxWriter(OrderNotificationOutboxMapper outbox, ObjectMapper json) { this.outbox = outbox; this.json = json; }
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(OrderNotificationEvent event) {
        try {
            if (outbox.insert(event.eventId(), event.orderId(), event.orderVersion(), json.writeValueAsString(event)) != 1)
                throw new IllegalStateException("订单通知事件保存失败");
        } catch (JsonProcessingException error) { throw new IllegalStateException("订单通知事件编码失败", error); }
    }
}
