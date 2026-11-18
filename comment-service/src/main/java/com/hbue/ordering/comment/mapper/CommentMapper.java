package com.hbue.ordering.comment.mapper;

import com.hbue.ordering.comment.api.ProductRatingVO;
import com.hbue.ordering.comment.model.CommentRowDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CommentMapper {
    /**
     * 按评论 ID 读取有效评论；已软删除的记录不返回。
     */
    @Select("""
            SELECT comment_id, user_id, author_name, order_id, product_id, window_id,
                   score, request_key, request_hash, created_at, has_images
            FROM comments
            WHERE comment_id = #{id} AND status = 1
            """)
    CommentRowDO byId(@Param("id") String id);

    /**
     * 按本人和幂等请求键查找已有评论，供重复提交时复用原结果。
     */
    @Select("""
            SELECT comment_id, user_id, author_name, order_id, product_id, window_id,
                   score, request_key, request_hash, created_at, has_images
            FROM comments
            WHERE user_id = #{user} AND request_key = #{key}
            """)
    CommentRowDO byRequest(@Param("user") Long user, @Param("key") String key);

    /**
     * 按订单和商品查找既有评论，防止同一购买明细重复评价。
     */
    @Select("""
            SELECT comment_id, user_id, author_name, order_id, product_id, window_id,
                   score, request_key, request_hash, created_at, has_images
            FROM comments
            WHERE order_id = #{order} AND product_id = #{product}
            """)
    CommentRowDO byPurchase(@Param("order") Long order, @Param("product") Long product);

    /** 按本人读取评价页；游标、评分和图片筛选均限定在该用户范围内。 */
    @Select("""
            SELECT comment_id, user_id, author_name, order_id, product_id, window_id,
                   score, request_key, request_hash, created_at, has_images
            FROM comments
            WHERE status = 1
              AND user_id = #{userId}
              AND (#{score} IS NULL OR score = #{score})
              AND (#{hasImages} IS NULL OR has_images = 1)
              AND (#{before} IS NULL OR id < (
                  SELECT cursor_row.id
                  FROM comments cursor_row
                  WHERE cursor_row.comment_id = #{before}
                    AND cursor_row.status = 1
                    AND cursor_row.user_id = #{userId}
                    AND (#{score} IS NULL OR cursor_row.score = #{score})
                    AND (#{hasImages} IS NULL OR cursor_row.has_images = 1)
              ))
            ORDER BY id DESC
            LIMIT #{size}
            """)
    List<CommentRowDO> pageByUser(@Param("userId") Long userId, @Param("before") String before,
                                  @Param("size") int size, @Param("score") Integer score,
                                  @Param("hasImages") Boolean hasImages);

    /** 按商品读取评价页；游标、评分和图片筛选均限定在该商品范围内。 */
    @Select("""
            SELECT comment_id, user_id, author_name, order_id, product_id, window_id,
                   score, request_key, request_hash, created_at, has_images
            FROM comments
            WHERE status = 1
              AND product_id = #{productId}
              AND (#{score} IS NULL OR score = #{score})
              AND (#{hasImages} IS NULL OR has_images = 1)
              AND (#{before} IS NULL OR id < (
                  SELECT cursor_row.id
                  FROM comments cursor_row
                  WHERE cursor_row.comment_id = #{before}
                    AND cursor_row.status = 1
                    AND cursor_row.product_id = #{productId}
                    AND (#{score} IS NULL OR cursor_row.score = #{score})
                    AND (#{hasImages} IS NULL OR cursor_row.has_images = 1)
              ))
            ORDER BY id DESC
            LIMIT #{size}
            """)
    List<CommentRowDO> pageByProduct(@Param("productId") Long productId, @Param("before") String before,
                                     @Param("size") int size, @Param("score") Integer score,
                                     @Param("hasImages") Boolean hasImages);

    /**
     * 按本人和订单读取已提交的商品评论，并按评论 ID 稳定排序。
     */
    @Select("""
            SELECT comment_id, user_id, author_name, order_id, product_id, window_id,
                   score, request_key, request_hash, created_at, has_images
            FROM comments
            WHERE user_id = #{user} AND order_id = #{order}
            ORDER BY id
            """)
    List<CommentRowDO> byOrder(@Param("user") Long user, @Param("order") Long order);

    @Select("SELECT review_count,score_total FROM comment_rating_stats WHERE product_id=#{id}")
    Summary summaryRow(@Param("id") Long id);

    default ProductRatingVO summary(Long productId) {
        Summary row = summaryRow(productId);
        return row == null ? ProductRatingVO.of(productId, 0, 0) : ProductRatingVO.of(productId, row.reviewCount(), row.scoreTotal());
    }

    /**
     * 按目标索引定位后聚合全部可见元数据，不读取Mongo正文；空集合数量为0、平均分为null。
     */
    @Select("SELECT COUNT(*) AS review_count, AVG(score) AS average_score, "
            + "COALESCE(SUM(score=5),0) AS positive_count, COALESCE(SUM(score=1),0) AS negative_count, "
            + "CASE WHEN COALESCE(SUM(has_images IS NULL),0)>0 THEN NULL ELSE COALESCE(SUM(has_images=1),0) END AS image_count "
            + "FROM comments WHERE product_id=#{id} AND status=1")
    com.hbue.ordering.comment.vo.ReviewSummaryVO reviewSummary(@Param("id") Long id);

    /**
     * 仅针对当前本人或商品检查未知图片标记；有图查询不得把待回填记录当成无图。
     */
    @Select("SELECT EXISTS(SELECT 1 FROM comments WHERE status=1 AND has_images IS NULL "
            + "AND ((#{user} IS NOT NULL AND user_id=#{user}) OR (#{user} IS NULL AND product_id=#{product})) LIMIT 1)")
    boolean hasUnknownImages(@Param("user") Long user, @Param("product") Long product);

    record Summary(long reviewCount, long scoreTotal) {
    }
}
