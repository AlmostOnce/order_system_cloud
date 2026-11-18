package com.hbue.ordering.comment.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RatingBatchMapper {
    /**
     * 批量读取窗口评分；调用方限制 ID 数量和取值，foreach 中的 ID 仍使用绑定参数。
     */
    @Select("""
            <script>
            SELECT window_id AS id, COUNT(*) AS cnt, AVG(score) AS avg_score
            FROM window_reviews
            WHERE window_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">
                #{id}
            </foreach>
            GROUP BY window_id
            </script>
            """)
    List<Row> windows(@Param("ids") List<Long> ids);

    /**
     * 批量读取商品评分统计；评分计数为零时平均分返回 null，ID 通过 foreach 绑定。
     */
    @Select("""
            <script>
            SELECT product_id AS id,
                   review_count AS cnt,
                   score_total / NULLIF(review_count, 0) AS avg_score
            FROM comment_rating_stats
            WHERE product_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">
                #{id}
            </foreach>
            </script>
            """)
    List<Row> products(@Param("ids") List<Long> ids);

    record Row(Long id, long count, Double average) {
    }
}
