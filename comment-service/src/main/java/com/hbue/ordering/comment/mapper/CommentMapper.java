package com.hbue.ordering.comment.mapper;

import com.hbue.ordering.comment.api.ProductRatingVO;
import com.hbue.ordering.comment.model.CommentRowDO;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CommentMapper {
    String COLUMNS = "comment_id,user_id,author_name,order_id,product_id,window_id,score,request_key,request_hash,created_at";
    record Summary(long reviewCount, long scoreTotal) {}
    @Select("SELECT " + COLUMNS + " FROM comments WHERE comment_id=#{id} AND status=1")
    CommentRowDO byId(@Param("id") String id);
    @Select("SELECT " + COLUMNS + " FROM comments WHERE user_id=#{user} AND request_key=#{key}")
    CommentRowDO byRequest(@Param("user") Long user, @Param("key") String key);
    @Select("SELECT " + COLUMNS + " FROM comments WHERE order_id=#{order} AND product_id=#{product}")
    CommentRowDO byPurchase(@Param("order") Long order, @Param("product") Long product);
    @Select("""
        <script>SELECT comment_id,user_id,author_name,order_id,product_id,window_id,score,request_key,request_hash,created_at
        FROM comments WHERE status=1
        <choose><when test="user != null">AND user_id=#{user}
          <if test="before != null">AND id &lt; (SELECT id FROM comments WHERE comment_id=#{before} AND user_id=#{user} AND status=1)</if>
        </when><otherwise>AND product_id=#{product}
          <if test="before != null">AND id &lt; (SELECT id FROM comments WHERE comment_id=#{before} AND product_id=#{product} AND status=1)</if>
        </otherwise></choose> ORDER BY id DESC LIMIT #{size}</script>
        """)
    List<CommentRowDO> page(@Param("user") Long user, @Param("product") Long product, @Param("before") String before, @Param("size") int size);
    @Select("SELECT " + COLUMNS + " FROM comments WHERE user_id=#{user} AND order_id=#{order} ORDER BY id")
    List<CommentRowDO> byOrder(@Param("user") Long user, @Param("order") Long order);
    @Select("SELECT review_count,score_total FROM comment_rating_stats WHERE product_id=#{id}")
    Summary summaryRow(@Param("id") Long id);
    default ProductRatingVO summary(Long productId) {
        Summary row = summaryRow(productId);
        return row == null ? ProductRatingVO.of(productId, 0, 0) : ProductRatingVO.of(productId, row.reviewCount(), row.scoreTotal());
    }
}
