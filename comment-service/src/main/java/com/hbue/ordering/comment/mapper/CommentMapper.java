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
                   score, request_key, request_hash, created_at, has_images, anonymous
            FROM comments
            WHERE comment_id = #{id} AND status = 1
            """)
    CommentRowDO byId(@Param("id") String id);

    /**
     * 按本人和幂等请求键查找未删除的评论，供重复提交时复用原结果。
     *
     * <p>带 {@code status = 1}：评价被删除后，同一个请求键不能再被当作"已经创建成功"回放，
     * 否则客户端会拿到一条实际已删除的评价。</p>
     */
    @Select("""
            SELECT comment_id, user_id, author_name, order_id, product_id, window_id,
                   score, request_key, request_hash, created_at, has_images, anonymous
            FROM comments
            WHERE user_id = #{user} AND request_key = #{key} AND status = 1
            """)
    CommentRowDO byRequest(@Param("user") Long user, @Param("key") String key);

    /**
     * 按订单和商品查找既有评论，防止同一购买明细重复评价。
     *
     * <p>刻意不带状态过滤：唯一键 {@code uk_comment_purchase} 对已删除评价同样成立，
     * 这里必须能查到它，才能把"已经评价过、后来删除了"和"从未评价"区分开。</p>
     */
    @Select("""
            SELECT comment_id, user_id, author_name, order_id, product_id, window_id,
                   score, request_key, request_hash, created_at, has_images, anonymous
            FROM comments
            WHERE order_id = #{order} AND product_id = #{product}
            """)
    CommentRowDO byPurchase(@Param("order") Long order, @Param("product") Long product);

    /** 按本人读取评价页；游标、评分和图片筛选均限定在该用户范围内。 */
    @Select("""
            SELECT comment_id, user_id, author_name, order_id, product_id, window_id,
                   score, request_key, request_hash, created_at, has_images, anonymous
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
                   score, request_key, request_hash, created_at, has_images, anonymous
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
     * 校验分页游标是否仍属于当前查询范围与筛选条件。
     *
     * <p>分页 SQL 用游标子查询定位起始位置，且子查询同样带上范围与筛选条件。若用户切换评分或
     * 图片筛选后沿用旧游标，子查询取不到 ID 会让整页静默变空；这里先按同一组条件回读游标，
     * 返回 0 时由服务层明确提示重新加载，而不是给用户一个空页。</p>
     *
     * @param before    游标（评价公开标识）
     * @param userId    本人范围，与 productId 互斥，非空时按用户过滤
     * @param productId 商品范围，与 userId 互斥，非空时按商品过滤
     * @param score     评分筛选，可空
     * @param hasImages 是否只看带图，可空
     * @return 命中条数，0 表示游标与当前筛选不匹配或已删除
     */
    @Select("""
            SELECT COUNT(*)
            FROM comments
            WHERE comment_id = #{before}
              AND status = 1
              AND (#{userId} IS NULL OR user_id = #{userId})
              AND (#{productId} IS NULL OR product_id = #{productId})
              AND (#{score} IS NULL OR score = #{score})
              AND (#{hasImages} IS NULL OR has_images = 1)
            """)
    int countScopedCursor(@Param("before") String before, @Param("userId") Long userId,
                          @Param("productId") Long productId, @Param("score") Integer score,
                          @Param("hasImages") Boolean hasImages);

    /**
     * 按本人和订单读取已提交且未删除的商品评论，并按评论 ID 稳定排序。
     *
     * <p>订单详情会按这道菜回显"已评价"状态，已删除的评价不能再算作已评价。</p>
     */
    @Select("""
            SELECT comment_id, user_id, author_name, order_id, product_id, window_id,
                   score, request_key, request_hash, created_at, has_images, anonymous
            FROM comments
            WHERE user_id = #{user} AND order_id = #{order} AND status = 1
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

    /**
     * 批量读取用户指定订单下已提交且未删除的菜品评价。
     *
     * <p>用于统计待评价订单：评价服务拿到订单服务的候选订单后，一次查出这些订单中
     * 已评价的菜品，避免逐单逐菜查询。已删除评价不算已评价，因此必须带 {@code status = 1}；
     * 只返回订单号与菜品号，不读取正文，也不跨用户查询。</p>
     *
     * @param userId   用户内部编号
     * @param orderIds 候选订单编号，调用方保证非空且数量有限
     * @return 已评价的订单与菜品组合
     */
    @Select("""
            <script>
            SELECT order_id AS orderId, product_id AS productId
            FROM comments
            WHERE user_id = #{userId} AND status = 1
              AND order_id IN
              <foreach collection="orderIds" item="orderId" open="(" separator="," close=")">#{orderId}</foreach>
            </script>
            """)
    List<ReviewedProduct> reviewedProducts(@Param("userId") Long userId,
                                           @Param("orderIds") List<Long> orderIds);

    record Summary(long reviewCount, long scoreTotal) {
    }

    /**
     * 已评价菜品投影。
     *
     * @param orderId   订单编号
     * @param productId 菜品编号
     */
    record ReviewedProduct(Long orderId, Long productId) {
    }
}
