package com.hbue.ordering.order.notification;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CancellationNotificationTccStore {
    public record Row(long orderId, int orderVersion, long recipientId, String status) {}
    private final JdbcTemplate jdbc;
    public CancellationNotificationTccStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void insertIfMissing(String xid, long orderId, int version, long recipientId, String status) {
        jdbc.update("""
            INSERT INTO order_notification_tcc(xid,order_id,order_version,recipient_id,status) VALUES(?,?,?,?,?)
            ON DUPLICATE KEY UPDATE xid=xid
            """, xid, orderId, version, recipientId, status);
    }
    public Row lock(String xid) {
        var rows = jdbc.query("SELECT order_id,order_version,recipient_id,status FROM order_notification_tcc WHERE xid=? FOR UPDATE",
                (r,n) -> new Row(r.getLong(1),r.getInt(2),r.getLong(3),r.getString(4)), xid);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    public void setStatus(String xid, String status) { jdbc.update("UPDATE order_notification_tcc SET status=? WHERE xid=?", status, xid); }
}
