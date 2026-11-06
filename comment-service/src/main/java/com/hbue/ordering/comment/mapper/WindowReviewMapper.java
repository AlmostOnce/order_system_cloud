package com.hbue.ordering.comment.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface WindowReviewMapper {
    record Row(String id, Long userId, String author, Long orderId, Long windowId, int score,
            String content, String images, String tags, String requestKey, String requestHash, LocalDateTime createdAt) {}
    record Rating(long reviewCount, Double averageScore) {}
    String COLUMNS = "review_id,user_id,author_name,order_id,window_id,score,content,image_file_keys,tags,request_key,request_hash,created_at";
    @Select("SELECT " + COLUMNS + " FROM window_reviews WHERE user_id=#{user} AND request_key=#{key}")
    Row byRequest(@Param("user") Long user, @Param("key") String key);
    @Select("SELECT " + COLUMNS + " FROM window_reviews WHERE user_id=#{user} AND order_id=#{order}")
    Row byOrder(@Param("user") Long user, @Param("order") Long order);
    @Insert("""
        INSERT INTO window_reviews(review_id,user_id,author_name,order_id,window_id,score,content,image_file_keys,tags,request_key,request_hash,created_at)
        VALUES(#{row.id},#{row.userId},#{row.author},#{row.orderId},#{row.windowId},#{row.score},#{row.content},#{row.images},#{row.tags},#{row.requestKey},#{row.requestHash},#{row.createdAt})
        """)
    int insert(@Param("row") Row row);
    @Select("SELECT COUNT(*) AS review_count, AVG(score) AS average_score FROM window_reviews WHERE window_id=#{windowId}")
    Rating rating(@Param("windowId") Long windowId);
    /** 按目标/本人及可选一星或五星筛选后取limit+1；游标子查询复核同一分数及有图范围。 */
    @Select("""
        <script>SELECT review_id,user_id,author_name,order_id,window_id,score,content,image_file_keys,tags,request_key,request_hash,created_at
        FROM window_reviews WHERE
        <choose><when test="user != null">user_id=#{user}
          <if test="cursor != null">AND id &lt; (SELECT id FROM window_reviews WHERE review_id=#{cursor} AND user_id=#{user} <if test="score != null">AND score=#{score}</if> <if test="hasImages != null">AND has_images=1</if>)</if>
        </when><otherwise>window_id=#{window}
          <if test="cursor != null">AND id &lt; (SELECT id FROM window_reviews WHERE review_id=#{cursor} AND window_id=#{window} <if test="score != null">AND score=#{score}</if> <if test="hasImages != null">AND has_images=1</if>)</if>
        </otherwise></choose>
        <if test="score != null">AND score=#{score}</if> <if test="hasImages != null">AND has_images=1</if> ORDER BY id DESC LIMIT #{limit}</script>
        """)
    List<Row> page(@Param("user") Long user, @Param("window") Long window, @Param("cursor") String cursor, @Param("limit") int limit, @Param("score") Integer score, @Param("hasImages") Boolean hasImages);
    /** 按目标索引定位后聚合全部可见元数据，不读取Mongo正文；空集合数量为0、平均分为null。 */
    @Select("SELECT COUNT(*) AS review_count, AVG(score) AS average_score, "
            + "COALESCE(SUM(score=5),0) AS positive_count, COALESCE(SUM(score=1),0) AS negative_count, "
            + "CASE WHEN COALESCE(SUM(has_images IS NULL),0)>0 THEN NULL ELSE COALESCE(SUM(has_images=1),0) END AS image_count "
            + "FROM window_reviews WHERE window_id=#{id}")
    com.hbue.ordering.comment.vo.ReviewSummaryVO reviewSummary(@Param("id") Long id);
}
