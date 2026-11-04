package com.hbue.ordering.order.service;
import com.hbue.ordering.order.dto.OrderTimeoutEvent;
import java.util.List;
public interface OrderTimeoutOutboxService {
    void record(OrderTimeoutEvent event);
    List<OrderTimeoutEvent> pending();
    void sent(String id);
    void failed(String id,String error);
    void deadLetter(String id);
}
