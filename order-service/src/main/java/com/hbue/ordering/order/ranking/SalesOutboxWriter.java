package com.hbue.ordering.order.ranking;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.order.mapper.SalesOutboxMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 强制加入修改订单状态的本地事务，禁止先提交状态后异步补写消息。 */
@Service
public class SalesOutboxWriter {
    private final SalesOutboxMapper mapper;
    private final ObjectMapper json;
    public SalesOutboxWriter(SalesOutboxMapper mapper, ObjectMapper json) { this.mapper = mapper; this.json = json; }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordPaid(Long orderId) {
        var event = OrderPaidSalesEvent.of(orderId);
        try {
            if (mapper.insert(event.eventId(), orderId, json.writeValueAsString(event)) != 1) {
                throw new IllegalStateException("支付销量事件保存失败");
            }
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("支付销量事件编码失败", exception);
        }
    }
}
