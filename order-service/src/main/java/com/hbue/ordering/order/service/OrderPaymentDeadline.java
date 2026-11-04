package com.hbue.ordering.order.service;
import com.hbue.ordering.order.model.OrderDO;
import java.time.*;
/** 订单创建时固定十五分钟；历史记录迁移前仅按原创建时间推导。 */
public final class OrderPaymentDeadline {
    private OrderPaymentDeadline() {}
    public static long epochMillis(OrderDO order) {
        LocalDateTime deadline=order.getExpiresAt();
        if(deadline==null && order.getCreatedAt()!=null) deadline=order.getCreatedAt().plusMinutes(15);
        if(deadline==null) throw new IllegalStateException("订单缺少支付截止时间");
        return deadline.atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
    }
}
