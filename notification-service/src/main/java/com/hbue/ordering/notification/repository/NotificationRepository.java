package com.hbue.ordering.notification.repository;

import com.hbue.ordering.notification.vo.NotificationVO;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationRepository {
    private static final RowMapper<NotificationVO> ROW = (row, index) -> new NotificationVO(
            row.getString("notification_id"), row.getString("notification_type"), row.getString("title"),
            row.getString("content"), row.getObject("order_id", Long.class),
            row.getTimestamp("created_at").toLocalDateTime(),
            row.getTimestamp("read_at") == null ? null : row.getTimestamp("read_at").toLocalDateTime());
    private final JdbcTemplate jdbc;
    public NotificationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<NotificationVO> page(long userId, String cursor, int size) {
        return jdbc.query("""
            SELECT * FROM notifications WHERE recipient_id=?
            AND (? IS NULL OR id < (SELECT id FROM notifications WHERE notification_id=? AND recipient_id=?))
            ORDER BY id DESC LIMIT ?
            """, ROW, userId, cursor, cursor, userId, size);
    }

    public boolean existsForUser(long userId, String id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM notifications WHERE recipient_id=? AND notification_id=?)",
                Boolean.class, userId, id));
    }

    public long unreadCount(long userId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE recipient_id=? AND read_at IS NULL", Long.class, userId);
        return count == null ? 0 : count;
    }

    public int markRead(long userId, String id) {
        return jdbc.update("UPDATE notifications SET read_at=COALESCE(read_at,CURRENT_TIMESTAMP(3)) WHERE recipient_id=? AND notification_id=?", userId, id);
    }

    public int markAllRead(long userId) {
        return jdbc.update("UPDATE notifications SET read_at=CURRENT_TIMESTAMP(3) WHERE recipient_id=? AND read_at IS NULL", userId);
    }

    public void insertOrderEvent(String id, long userId, String eventId, long orderId, String content) {
        jdbc.update("""
            INSERT INTO notifications(notification_id,recipient_id,event_id,notification_type,title,content,order_id)
            VALUES(?,?,?,'ORDER_STATUS','订单状态更新',?,?)
            """, id, userId, eventId, content, orderId);
    }
    public boolean existsEvent(long userId, String eventId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM notifications WHERE recipient_id=? AND event_id=?)", Boolean.class, userId, eventId));
    }
}
