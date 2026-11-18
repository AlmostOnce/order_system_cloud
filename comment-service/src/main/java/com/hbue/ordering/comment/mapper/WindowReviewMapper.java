package com.hbue.ordering.comment.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface WindowReviewMapper {
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

    /** 按本人读取档口评价页；游标、评分和图片筛选均限定在该用户范围内。 */
    @Select("""
            SELECT review_id, user_id, author_name, order_id, window_id, score,
                   content, image_file_keys, tags, request_key, request_hash, created_at
            FROM window_reviews
            WHERE user_id = #{userId}
              AND (#{score} IS NULL OR score = #{score})
              AND (#{hasImages} IS NULL OR has_images = 1)
              AND (#{cursor} IS NULL OR id < (
                  SELECT cursor_row.id
                  FROM window_reviews cursor_row
                  WHERE cursor_row.review_id = #{cursor}
                    AND cursor_row.user_id = #{userId}
                    AND (#{score} IS NULL OR cursor_row.score = #{score})
                    AND (#{hasImages} IS NULL OR cursor_row.has_images = 1)
              ))
            ORDER BY id DESC
            LIMIT #{limit}
            """)
    List<Row> pageByUser(@Param("userId") Long userId, @Param("cursor") String cursor,
                         @Param("limit") int limit, @Param("score") Integer score,
                         @Param("hasImages") Boolean hasImages);

    /** 按档口读取评价页；游标、评分和图片筛选均限定在该档口范围内。 */
    @Select("""
            SELECT review_id, user_id, author_name, order_id, window_id, score,
                   content, image_file_keys, tags, request_key, request_hash, created_at
            FROM window_reviews
            WHERE window_id = #{windowId}
              AND (#{score} IS NULL OR score = #{score})
              AND (#{hasImages} IS NULL OR has_images = 1)
              AND (#{cursor} IS NULL OR id < (
                  SELECT cursor_row.id
                  FROM window_reviews cursor_row
                  WHERE cursor_row.review_id = #{cursor}
                    AND cursor_row.window_id = #{windowId}
                    AND (#{score} IS NULL OR cursor_row.score = #{score})
                    AND (#{hasImages} IS NULL OR cursor_row.has_images = 1)
              ))
            ORDER BY id DESC
            LIMIT #{limit}
            """)
    List<Row> pageByWindow(@Param("windowId") Long windowId, @Param("cursor") String cursor,
                           @Param("limit") int limit, @Param("score") Integer score,
                           @Param("hasImages") Boolean hasImages);

    /**
     * 按目标索引定位后聚合全部可见元数据，不读取Mongo正文；空集合数量为0、平均分为null。
     */
    @Select("SELECT COUNT(*) AS review_count, AVG(score) AS average_score, "
            + "COALESCE(SUM(score=5),0) AS positive_count, COALESCE(SUM(score=1),0) AS negative_count, "
            + "CASE WHEN COALESCE(SUM(has_images IS NULL),0)>0 THEN NULL ELSE COALESCE(SUM(has_images=1),0) END AS image_count "
            + "FROM window_reviews WHERE window_id=#{id}")
    com.hbue.ordering.comment.vo.ReviewSummaryVO reviewSummary(@Param("id") Long id);

    record Row(String id, Long userId, String author, Long orderId, Long windowId, int score,
               String content, String images, String tags, String requestKey, String requestHash,
               LocalDateTime createdAt) {
    }

    record Rating(long reviewCount, Double averageScore) {
    }
}
