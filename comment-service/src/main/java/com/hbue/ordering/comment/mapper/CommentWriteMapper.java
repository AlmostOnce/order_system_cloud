package com.hbue.ordering.comment.mapper;

import com.hbue.ordering.comment.model.CommentRowDO;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CommentWriteMapper {
    /**
     * 有图标记与元数据、评分、Outbox在同一事务写入，不依赖异步修补新评价。
     */
    @Insert("""
            INSERT INTO comments(comment_id,user_id,author_name,order_id,product_id,window_id,score,request_key,request_hash,created_at,has_images)
            VALUES(#{row.commentId},#{row.userId},#{row.authorName},#{row.orderId},#{row.productId},#{row.windowId},#{row.score},#{row.requestKey},#{row.requestHash},#{row.createdAt},#{row.hasImages})
            """)
    int insertComment(@Param("row") CommentRowDO row);

    @Insert("""
            INSERT INTO comment_rating_stats(product_id,review_count,score_total,revision) VALUES(#{productId},1,#{score},1)
            ON DUPLICATE KEY UPDATE review_count=review_count+1,score_total=score_total+VALUES(score_total),revision=revision+1
            """)
    int updateRating(@Param("productId") Long productId, @Param("score") int score);

    /**
     * 修改未删除评价的评分与带图标记。
     *
     * <p>只更新 {@code status = 1} 的记录：返回 0 说明评价已被删除或并发状态已变化，
     * 调用方必须据此失败，不能把"没改到"当作修改成功。
     * {@code has_images} 由本次正文图片数量推导后写入，与评分一起在同一事务内生效。</p>
     *
     * @param id        公开评价标识
     * @param score     新评分
     * @param hasImages 修改后是否带图
     * @return 受影响行数，正常为 1
     */
    @Update("""
            UPDATE comments SET score=#{score}, has_images=#{hasImages}, updated_at=NOW(6)
            WHERE comment_id=#{id} AND status=1
            """)
    int updateComment(@Param("id") String id, @Param("score") int score, @Param("hasImages") boolean hasImages);

    /**
     * 软删除未发布状态之外的评价。
     *
     * <p>保留正文与评分原始值，便于申诉与追责；列表、评分与汇总查询都带 {@code status = 1}，
     * 因此删除后立即不可见。重复删除返回 0，由调用方按"已删除"处理。</p>
     *
     * @param id 公开评价标识
     * @return 受影响行数，正常为 1
     */
    @Update("UPDATE comments SET status=2, updated_at=NOW(6) WHERE comment_id=#{id} AND status=1")
    int deleteComment(@Param("id") String id);

    /**
     * 按增量调整菜品评分汇总，并递增版本号。
     *
     * <p>SQL 中带上界条件（调整后条数与总分都不能为负），命中不到行时返回 0，
     * 由调用方抛出异常回滚整个本地事务，避免汇总与评价表出现不一致。
     * 删除最后一条评价时 {@code countDelta = -1}、{@code scoreDelta = -原评分}，
     * 结果条数与总分同时归零，符合放宽后的区间约束。</p>
     *
     * @param productId  菜品编号
     * @param countDelta 评价条数增量
     * @param scoreDelta 评分总和增量
     * @return 受影响行数，正常为 1
     */
    @Update("""
            UPDATE comment_rating_stats
            SET review_count = review_count + #{countDelta},
                score_total = score_total + #{scoreDelta},
                revision = revision + 1
            WHERE product_id = #{productId}
              AND review_count + #{countDelta} >= 0
              AND score_total + #{scoreDelta} >= 0
            """)
    int adjustRating(@Param("productId") Long productId,
                     @Param("countDelta") int countDelta,
                     @Param("scoreDelta") int scoreDelta);

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

    record RatingSnapshot(long revision, long reviewCount, long scoreTotal) {
    }

    record OutboxRow(String id, String payload) {
    }
}
