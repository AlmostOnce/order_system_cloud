package com.hbue.ordering.comment.repository;

import com.hbue.ordering.comment.api.ProductRatingVO;
import com.hbue.ordering.comment.model.CommentRowDO;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class CommentRepository {
    private final JdbcTemplate jdbc;
    private static final RowMapper<CommentRowDO> ROW = (r, n) -> new CommentRowDO(
            r.getString("comment_id"), r.getLong("user_id"), r.getString("author_name"), r.getLong("order_id"),
            r.getLong("product_id"), r.getLong("window_id"), r.getInt("score"), r.getString("request_key"),
            r.getString("request_hash"), r.getTimestamp("created_at").toLocalDateTime());
    public CommentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public CommentRowDO byId(String id) { return first(jdbc.query("SELECT * FROM comments WHERE comment_id=? AND status=1", ROW, id)); }
    public CommentRowDO byRequest(Long user, String key) {
        return first(jdbc.query("SELECT * FROM comments WHERE user_id=? AND request_key=?", ROW, user, key));
    }
    public CommentRowDO byPurchase(Long order, Long product) {
        return first(jdbc.query("SELECT * FROM comments WHERE order_id=? AND product_id=?", ROW, order, product));
    }
    /** 游标使用公开UUID；内部自增ID只参与排序，不返回客户端。 */
    public List<CommentRowDO> page(Long user, Long product, String before, int size) {
        if (user != null) return jdbc.query("""
                SELECT * FROM comments WHERE user_id=? AND status=1
                AND (? IS NULL OR id < (SELECT id FROM comments WHERE comment_id=? AND user_id=? AND status=1))
                ORDER BY id DESC LIMIT ?
                """, ROW, user, before, before, user, size);
        return jdbc.query("""
                SELECT * FROM comments WHERE product_id=? AND status=1
                AND (? IS NULL OR id < (SELECT id FROM comments WHERE comment_id=? AND product_id=? AND status=1))
                ORDER BY id DESC LIMIT ?
                """, ROW, product, before, before, product, size);
    }
    public ProductRatingVO summary(Long productId) {
        var rows = jdbc.query("SELECT review_count,score_total FROM comment_rating_stats WHERE product_id=?",
                (r,n) -> ProductRatingVO.of(productId, r.getLong(1), r.getLong(2)), productId);
        return rows.isEmpty() ? ProductRatingVO.of(productId, 0, 0) : rows.getFirst();
    }
    private CommentRowDO first(List<CommentRowDO> rows) { return rows.isEmpty() ? null : rows.getFirst(); }
}
