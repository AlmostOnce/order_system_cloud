package com.hbue.ordering.comment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.comment.api.ProductRatingChangedEvent;
import com.hbue.ordering.comment.model.CommentRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** MySQL元数据、评分和outbox在同一个本地事务内提交，不包含Mongo事务。 */
@Service
public class CommentMetadataWriter {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public CommentMetadataWriter(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
    @Transactional(timeout = 15)
    public void commit(CommentRow row) {
        jdbc.update("""
            INSERT INTO comments(comment_id,user_id,author_name,order_id,product_id,window_id,score,request_key,request_hash,created_at)
            VALUES(?,?,?,?,?,?,?,?,?,?)
            """, row.commentId(), row.userId(), row.authorName(), row.orderId(), row.productId(), row.windowId(),
                row.score(), row.requestKey(), row.requestHash(), row.createdAt());
        jdbc.update("""
            INSERT INTO comment_rating_stats(product_id,review_count,score_total,revision) VALUES(?,1,?,1)
            ON DUPLICATE KEY UPDATE review_count=review_count+1,score_total=score_total+VALUES(score_total),revision=revision+1
            """, row.productId(), row.score());
        // 上面的写入已持有该菜品行锁；同菜品并发提交的版本和快照保持一致。
        ProductRatingChangedEvent event = jdbc.queryForObject("""
            SELECT revision,review_count,score_total FROM comment_rating_stats WHERE product_id=? FOR UPDATE
            """, (r,n) -> new ProductRatingChangedEvent(1,row.commentId(),row.productId(),
                    r.getLong(1),r.getLong(2),r.getLong(3)), row.productId());
        try {
            jdbc.update("INSERT INTO comment_rating_outbox(event_id,product_id,revision,payload) VALUES(?,?,?,?)",
                    event.eventId(), event.productId(), event.revision(), json.writeValueAsString(event));
        } catch (JsonProcessingException exception) { throw new IllegalStateException("评分事件编码失败", exception); }
    }
}
