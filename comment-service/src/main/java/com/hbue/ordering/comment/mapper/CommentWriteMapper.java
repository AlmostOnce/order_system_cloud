package com.hbue.ordering.comment.mapper;

import com.hbue.ordering.comment.model.CommentRowDO;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CommentWriteMapper {
    record RatingSnapshot(long revision, long reviewCount, long scoreTotal) {}
    record OutboxRow(String id, String payload) {}
    @Insert("""
        INSERT INTO comments(comment_id,user_id,author_name,order_id,product_id,window_id,score,request_key,request_hash,created_at)
        VALUES(#{row.commentId},#{row.userId},#{row.authorName},#{row.orderId},#{row.productId},#{row.windowId},#{row.score},#{row.requestKey},#{row.requestHash},#{row.createdAt})
        """)
    int insertComment(@Param("row") CommentRowDO row);
    @Insert("""
        INSERT INTO comment_rating_stats(product_id,review_count,score_total,revision) VALUES(#{productId},1,#{score},1)
        ON DUPLICATE KEY UPDATE review_count=review_count+1,score_total=score_total+VALUES(score_total),revision=revision+1
        """)
    int updateRating(@Param("productId") Long productId, @Param("score") int score);
    @Select("SELECT revision,review_count,score_total FROM comment_rating_stats WHERE product_id=#{productId} FOR UPDATE")
    RatingSnapshot lockRating(@Param("productId") Long productId);
    @Insert("INSERT INTO comment_rating_outbox(event_id,product_id,revision,payload) VALUES(#{eventId},#{productId},#{revision},#{payload})")
    int insertOutbox(@Param("eventId") String eventId, @Param("productId") Long productId, @Param("revision") long revision, @Param("payload") String payload);
    @Select("SELECT event_id,payload FROM comment_rating_outbox WHERE published_at IS NULL AND next_attempt_at<=NOW() ORDER BY next_attempt_at,event_id LIMIT 50")
    java.util.List<OutboxRow> pending();
    @Update("UPDATE comment_rating_outbox SET published_at=NOW(),last_error=NULL WHERE event_id=#{id} AND published_at IS NULL")
    int markPublished(@Param("id") String id);
    @Update("""
        UPDATE comment_rating_outbox SET next_attempt_at=TIMESTAMPADD(SECOND,POW(2,LEAST(attempts,8)),NOW()),
        attempts=attempts+1,last_error=#{error} WHERE event_id=#{id} AND published_at IS NULL
        """)
    int retry(@Param("id") String id, @Param("error") String error);
    @Update("""
        UPDATE comment_rating_outbox o JOIN comment_rating_stats s ON o.product_id=s.product_id AND o.revision=s.revision
        SET o.published_at=NULL WHERE o.published_at IS NOT NULL
        """)
    int republishLatest();
}
