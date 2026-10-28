package com.hbue.ordering.order.notification;

import org.apache.seata.core.context.RootContext;
import org.apache.seata.rm.tcc.api.BusinessActionContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "notification.cancel-tcc.enabled", havingValue = "true")
public class CancellationNotificationTccImpl implements CancellationNotificationTcc {
    private final CancellationNotificationTccTransactions transactions;
    public CancellationNotificationTccImpl(CancellationNotificationTccTransactions transactions) { this.transactions = transactions; }
    public boolean prepare(Long orderId, Integer version, Long recipientId) {
        String xid = RootContext.getXID();
        if (xid == null || orderId == null || orderId <= 0 || version == null || version <= 0 || recipientId == null || recipientId <= 0)
            throw new IllegalStateException("取消通知缺少事务或订单信息");
        return transactions.prepare(xid, orderId, version, recipientId);
    }
    public boolean confirm(BusinessActionContext context) { return transactions.confirm(context.getXid()); }
    public boolean cancel(BusinessActionContext context) {
        return transactions.cancel(context.getXid(), number(context, "orderId"), Math.toIntExact(number(context, "orderVersion")), number(context, "recipientId"));
    }
    private static Long number(BusinessActionContext context, String name) {
        Object value = context.getActionContext(name);
        if (!(value instanceof Number number) || number.longValue() <= 0) throw new IllegalStateException("取消通知缺少 " + name);
        return number.longValue();
    }
}
