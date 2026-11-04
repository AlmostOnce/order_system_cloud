package com.hbue.ordering.order.service.impl;
import com.hbue.ordering.order.service.OrderTimeoutOutboxService;
import com.hbue.ordering.order.dto.OrderTimeoutEvent;
import com.hbue.ordering.order.mapper.OrderTimeoutOutboxMapper;
import com.hbue.ordering.order.model.OrderTimeoutOutboxDO;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
public class OrderTimeoutOutboxServiceImpl implements OrderTimeoutOutboxService {
    private final OrderTimeoutOutboxMapper mapper;
    public OrderTimeoutOutboxServiceImpl(OrderTimeoutOutboxMapper mapper) {this.mapper=mapper;}
    @Transactional(propagation=Propagation.MANDATORY)
    public void record(OrderTimeoutEvent event) {
        if(!event.valid())throw new IllegalArgumentException("无效超时事件");
        var row=new OrderTimeoutOutboxDO();row.setEventId(event.eventId());row.setOrderId(event.orderId());row.setExpiresAt(event.expiresAt());
        if(mapper.insert(row)!=1)throw new IllegalStateException("超时事件未持久化");
    }
    public List<OrderTimeoutEvent> pending() {return mapper.pending().stream().map(row->new OrderTimeoutEvent(row.getEventId(),row.getOrderId(),row.getExpiresAt())).toList();}
    public void sent(String id) {mapper.sent(id);}
    public void failed(String id,String error) {mapper.failed(id,error);}
    public void deadLetter(String id) {mapper.deadLetter(id);}
}
