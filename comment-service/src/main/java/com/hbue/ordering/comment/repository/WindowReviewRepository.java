package com.hbue.ordering.comment.repository;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class WindowReviewRepository {
    public record Row(String id, Long userId, String author, Long orderId, Long windowId, int score,
            String content, String images, String tags, String requestKey, String requestHash, LocalDateTime createdAt) {}
    private static final RowMapper<Row> MAPPER = (r, n) -> new Row(r.getString("review_id"), r.getLong("user_id"),
            r.getString("author_name"), r.getLong("order_id"), r.getLong("window_id"), r.getInt("score"),
            r.getString("content"), r.getString("image_file_keys"), r.getString("tags"), r.getString("request_key"),
            r.getString("request_hash"), r.getTimestamp("created_at").toLocalDateTime());
    private final JdbcTemplate jdbc;
    public WindowReviewRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Row byRequest(Long user, String key) {
        var rows = jdbc.query("SELECT * FROM window_reviews WHERE user_id=? AND request_key=?", MAPPER, user, key);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    public void insert(Row row) {
        jdbc.update("""
            INSERT INTO window_reviews(review_id,user_id,author_name,order_id,window_id,score,content,image_file_keys,tags,request_key,request_hash,created_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
            """, row.id(), row.userId(), row.author(), row.orderId(), row.windowId(), row.score(), row.content(),
                row.images(), row.tags(), row.requestKey(), row.requestHash(), row.createdAt());
    }
    public record Rating(long reviewCount, Double averageScore) {}
    public Rating rating(Long windowId) {
        return jdbc.queryForObject("SELECT COUNT(*) AS cnt, AVG(score) AS avg_score FROM window_reviews WHERE window_id=?",
                (r, n) -> new Rating(r.getLong("cnt"), r.getObject("avg_score") == null ? null : r.getDouble("avg_score")), windowId);
    }
    public List<Row> page(Long user, Long window, String cursor, int limit) {
        if (user != null) return jdbc.query("""
            SELECT * FROM window_reviews WHERE user_id=?
            AND (? IS NULL OR id < (SELECT id FROM window_reviews WHERE review_id=? AND user_id=?))
            ORDER BY id DESC LIMIT ?
            """, MAPPER, user, cursor, cursor, user, limit);
        return jdbc.query("""
            SELECT * FROM window_reviews WHERE window_id=?
            AND (? IS NULL OR id < (SELECT id FROM window_reviews WHERE review_id=? AND window_id=?))
            ORDER BY id DESC LIMIT ?
            """, MAPPER, window, cursor, cursor, window, limit);
    }
}
