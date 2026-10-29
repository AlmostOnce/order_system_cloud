package com.hbue.ordering.comment.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RatingBatchMapper {
    record Row(Long id, long count, Double average) {}
    @Select("""
        <script>SELECT window_id AS id, COUNT(*) AS cnt, AVG(score) AS avg_score FROM window_reviews
        WHERE window_id IN <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        GROUP BY window_id</script>
        """)
    List<Row> windows(@Param("ids") List<Long> ids);
    @Select("""
        <script>SELECT product_id AS id, review_count AS cnt, score_total / NULLIF(review_count, 0) AS avg_score
        FROM comment_rating_stats WHERE product_id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach></script>
        """)
    List<Row> products(@Param("ids") List<Long> ids);
}
