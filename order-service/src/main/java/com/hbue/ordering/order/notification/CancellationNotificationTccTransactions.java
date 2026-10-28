package com.hbue.ordering.order.notification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** TCC 二阶段通过独立 Spring 事务代理执行，保证状态与 outbox 原子提交。 */
@Service
public class CancellationNotificationTccTransactions {
    private final CancellationNotificationTccStore store;
    private final OrderNotificationOutboxWriter outbox;
    public CancellationNotificationTccTransactions(CancellationNotificationTccStore store, OrderNotificationOutboxWriter outbox) {
        this.store = store; this.outbox = outbox;
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean prepare(String xid, long orderId, int version, long recipientId) {
        store.insertIfMissing(xid, orderId, version, recipientId, "PREPARED");
        var row = store.lock(xid);
        if (row == null || row.orderId() != orderId || row.orderVersion() != version || row.recipientId() != recipientId || !"PREPARED".equals(row.status()))
            throw new IllegalStateException("取消通知事务已结束或参数不一致");
        return true;
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean confirm(String xid) {
        var row = store.lock(xid);
        if (row == null || "CANCELLED".equals(row.status())) throw new IllegalStateException("取消通知尚未准备或已回滚");
        if ("CONFIRMED".equals(row.status())) return true;
        if (!"PREPARED".equals(row.status())) throw new IllegalStateException("取消通知状态不正确");
        outbox.record(OrderNotificationEvent.of(row.orderId(), row.orderVersion(), row.recipientId(), "已取消"));
        store.setStatus(xid, "CONFIRMED");
        return true;
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean cancel(String xid, long orderId, int version, long recipientId) {
        store.insertIfMissing(xid, orderId, version, recipientId, "CANCELLED");
        var row = store.lock(xid);
        if (row == null || row.orderId() != orderId || row.orderVersion() != version || row.recipientId() != recipientId)
            throw new IllegalStateException("取消通知事务参数不一致");
        if ("CONFIRMED".equals(row.status())) throw new IllegalStateException("已确认的取消通知不能回滚");
        if (!"CANCELLED".equals(row.status())) store.setStatus(xid, "CANCELLED");
        return true;
    }
}
