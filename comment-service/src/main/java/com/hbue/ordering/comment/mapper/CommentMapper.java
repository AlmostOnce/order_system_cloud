package com.hbue.ordering.comment.mapper;

import com.hbue.ordering.comment.api.ProductRatingVO;
import com.hbue.ordering.comment.model.CommentRowDO;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CommentMapper {
    String COLUMNS = "comment_id,user_id,author_name,order_id,product_id,window_id,score,request_key,request_hash,created_at,has_images";
    record Summary(long reviewCount, long scoreTotal) {}
    @Select("SELECT " + COLUMNS + " FROM comments WHERE comment_id=#{id} AND status=1")
    CommentRowDO byId(@Param("id") String id);
    @Select("SELECT " + COLUMNS + " FROM comments WHERE user_id=#{user} AND request_key=#{key}")
    CommentRowDO byRequest(@Param("user") Long user, @Param("key") String key);
    @Select("SELECT " + COLUMNS + " FROM comments WHERE order_id=#{order} AND product_id=#{product}")
    CommentRowDO byPurchase(@Param("order") Long order, @Param("product") Long product);
    /** 按目标/本人及可选一星或五星筛选后取limit+1；游标子查询复核同一分数及有图范围。 */
    @Select("""
        <script>SELECT comment_id,user_id,author_name,order_id,product_id,window_id,score,request_key,request_hash,created_at,has_images
        FROM comments WHERE status=1
        <if test="score != null">AND score=#{score}</if> <if test="hasImages != null">AND has_images=1</if>
        <choose><when test="user != null">AND user_id=#{user}
          <if test="before != null">AND id &lt; (SELECT id FROM comments WHERE comment_id=#{before} AND user_id=#{user} AND status=1 <if test="score != null">AND score=#{score}</if> <if test="hasImages != null">AND has_images=1</if>)</if>
        </when><otherwise>AND product_id=#{product}
          <if test="before != null">AND id &lt; (SELECT id FROM comments WHERE comment_id=#{before} AND product_id=#{product} AND status=1 <if test="score != null">AND score=#{score}</if> <if test="hasImages != null">AND has_images=1</if>)</if>
        </otherwise></choose> ORDER BY id DESC LIMIT #{size}</script>
        """)
    List<CommentRowDO> page(@Param("user") Long user, @Param("product") Long product, @Param("before") String before, @Param("size") int size, @Param("score") Integer score, @Param("hasImages") Boolean hasImages);
    @Select("SELECT " + COLUMNS + " FROM comments WHERE user_id=#{user} AND order_id=#{order} ORDER BY id")
    List<CommentRowDO> byOrder(@Param("user") Long user, @Param("order") Long order);
    @Select("SELECT review_count,score_total FROM comment_rating_stats WHERE product_id=#{id}")
    Summary summaryRow(@Param("id") Long id);
    default ProductRatingVO summary(Long productId) {
        Summary row = summaryRow(productId);
        return row == null ? ProductRatingVO.of(productId, 0, 0) : ProductRatingVO.of(productId, row.reviewCount(), row.scoreTotal());
    }
    /** 按目标索引定位后聚合全部可见元数据，不读取Mongo正文；空集合数量为0、平均分为null。 */
    @Select("SELECT COUNT(*) AS review_count, AVG(score) AS average_score, "
            + "COALESCE(SUM(score=5),0) AS positive_count, COALESCE(SUM(score=1),0) AS negative_count, "
            + "CASE WHEN COALESCE(SUM(has_images IS NULL),0)>0 THEN NULL ELSE COALESCE(SUM(has_images=1),0) END AS image_count "
            + "FROM comments WHERE product_id=#{id} AND status=1")
    com.hbue.ordering.comment.vo.ReviewSummaryVO reviewSummary(@Param("id") Long id);
    /** 仅针对当前对象/本人检查未知标记；有图查询不得把待回填当成空结果。 */
    @Select("<script>SELECT EXISTS(SELECT 1 FROM comments WHERE status=1 AND has_images IS NULL "
            + "<choose><when test='user != null'>AND user_id=#{user}</when>"
            + "<otherwise>AND product_id=#{product}</otherwise></choose> LIMIT 1)</script>")
    boolean hasUnknownImages(@Param("user") Long user, @Param("product") Long product);
}
