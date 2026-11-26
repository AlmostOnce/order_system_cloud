package com.hbue.ordering.comment.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 店铺评价的读写。
 *
 * <p>所有读取都只返回 {@code status = 1} 的记录：删除采用软删除，正文与图片留在库里便于追责，
 * 但已删除的评价不能出现在列表、评分与汇总里。唯一键仍然占用原订单，删除后不允许再对同一订单
 * 重复评价，这一限制由 {@code uk_window_review_order} 保证。</p>
 *
 * @author order-system
 * @date 2026-10-28
 */
@Mapper
public interface WindowReviewMapper {

    /**
     * 对外返回的列集合；不含 status 与 updated_at，避免内部字段泄漏到视图对象。
     */
    String COLUMNS = "review_id,user_id,author_name,order_id,window_id,score,content,image_file_keys,tags,request_key,request_hash,created_at";

    /**
     * 按公开评价标识读取未删除的评价。
     *
     * @param id 评价标识
     * @return 评价行；不存在或已删除时返回 null
     */
    @Select("SELECT " + COLUMNS + " FROM window_reviews WHERE review_id=#{id} AND status=1")
    Row byId(@Param("id") String id);

    /**
     * 按本人和幂等请求键查找未删除的评价，供重复提交时复用原结果。
     */
    @Select("SELECT " + COLUMNS + " FROM window_reviews WHERE user_id=#{user} AND request_key=#{key} AND status=1")
    Row byRequest(@Param("user") Long user, @Param("key") String key);

    /**
     * 按本人和订单查找未删除的评价，供"去评价"入口回显。
     */
    @Select("SELECT " + COLUMNS + " FROM window_reviews WHERE user_id=#{user} AND order_id=#{order} AND status=1")
    Row byOrder(@Param("user") Long user, @Param("order") Long order);

    @Insert("""
            INSERT INTO window_reviews(review_id,user_id,author_name,order_id,window_id,score,content,image_file_keys,tags,request_key,request_hash,created_at)
            VALUES(#{row.id},#{row.userId},#{row.author},#{row.orderId},#{row.windowId},#{row.score},#{row.content},#{row.images},#{row.tags},#{row.requestKey},#{row.requestHash},#{row.createdAt})
            """)
    int insert(@Param("row") Row row);

    /**
     * 修改评价内容与评分。
     *
     * <p>只更新未删除的记录，返回 0 表示评价已被删除或并发状态已变化，调用方必须据此失败，
     * 不能把"没改到"当作成功。{@code has_images} 由生成列根据图片数组自动维护，不在此写入。</p>
     *
     * @param id      评价标识
     * @param score   新评分
     * @param content 新正文
     * @param images  新图片标识 JSON 数组
     * @param tags    新标签 JSON 数组
     * @return 受影响行数，正常为 1
     */
    @Update("""
            UPDATE window_reviews
            SET score=#{score}, content=#{content}, image_file_keys=#{images}, tags=#{tags}, updated_at=NOW(6)
            WHERE review_id=#{id} AND status=1
            """)
    int update(@Param("id") String id, @Param("score") int score, @Param("content") String content,
               @Param("images") String images, @Param("tags") String tags);

    /**
     * 软删除评价。
     *
     * <p>幂等：已删除的记录不会被再次更新，返回 0 由调用方按"已删除"处理。</p>
     *
     * @param id 评价标识
     * @return 受影响行数，正常为 1
     */
    @Update("UPDATE window_reviews SET status=2, updated_at=NOW(6) WHERE review_id=#{id} AND status=1")
    int softDelete(@Param("id") String id);

    /**
     * 统计未删除评价的条数与平均分。
     */
    @Select("SELECT COUNT(*) AS review_count, AVG(score) AS average_score FROM window_reviews WHERE window_id=#{windowId} AND status=1")
    Rating rating(@Param("windowId") Long windowId);

    /**
     * 按本人读取档口评价页；游标、评分和图片筛选均限定在该用户范围内。
     */
    @Select("""
            SELECT review_id, user_id, author_name, order_id, window_id, score,
                   content, image_file_keys, tags, request_key, request_hash, created_at
            FROM window_reviews
            WHERE status = 1
              AND user_id = #{userId}
              AND (#{score} IS NULL OR score = #{score})
              AND (#{hasImages} IS NULL OR has_images = 1)
              AND (#{cursor} IS NULL OR id < (
                  SELECT cursor_row.id
                  FROM window_reviews cursor_row
                  WHERE cursor_row.review_id = #{cursor}
                    AND cursor_row.status = 1
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

    /**
     * 按档口读取评价页；游标、评分和图片筛选均限定在该档口范围内。
     */
    @Select("""
            SELECT review_id, user_id, author_name, order_id, window_id, score,
                   content, image_file_keys, tags, request_key, request_hash, created_at
            FROM window_reviews
            WHERE status = 1
              AND window_id = #{windowId}
              AND (#{score} IS NULL OR score = #{score})
              AND (#{hasImages} IS NULL OR has_images = 1)
              AND (#{cursor} IS NULL OR id < (
                  SELECT cursor_row.id
                  FROM window_reviews cursor_row
                  WHERE cursor_row.review_id = #{cursor}
                    AND cursor_row.status = 1
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
     * 按目标索引定位后聚合全部可见元数据，不读取正文以外的冗余列；空集合数量为0、平均分为null。
     */
    @Select("SELECT COUNT(*) AS review_count, AVG(score) AS average_score, "
            + "COALESCE(SUM(score=5),0) AS positive_count, COALESCE(SUM(score=1),0) AS negative_count, "
            + "CASE WHEN COALESCE(SUM(has_images IS NULL),0)>0 THEN NULL ELSE COALESCE(SUM(has_images=1),0) END AS image_count "
            + "FROM window_reviews WHERE window_id=#{id} AND status=1")
    com.hbue.ordering.comment.vo.ReviewSummaryVO reviewSummary(@Param("id") Long id);

    /**
     * 批量读取用户已提交且未删除的店铺评价所对应的订单。
     *
     * <p>用于统计待评价订单：店铺评价每个订单最多一条，只需返回订单号集合。
     * 已删除评价不算已评价，因此必须带 {@code status = 1}；不读取正文与图片。</p>
     *
     * @param userId   用户内部编号
     * @param orderIds 候选订单编号，调用方保证非空且数量有限
     * @return 已提交店铺评价的订单编号
     */
    @Select("""
            <script>
            SELECT order_id AS orderId
            FROM window_reviews
            WHERE user_id = #{userId} AND status = 1
              AND order_id IN
              <foreach collection="orderIds" item="orderId" open="(" separator="," close=")">#{orderId}</foreach>
            </script>
            """)
    List<Long> reviewedOrderIds(@Param("userId") Long userId,
                                @Param("orderIds") List<Long> orderIds);

    /**
     * 评价行的内部投影。
     *
     * @param id         公开评价标识
     * @param userId     作者编号
     * @param author     作者昵称快照
     * @param orderId    订单编号
     * @param windowId   档口编号
     * @param score      评分
     * @param content    正文
     * @param images     图片标识 JSON 数组
     * @param tags       标签 JSON 数组
     * @param requestKey 幂等键
     * @param requestHash 内容摘要
     * @param createdAt  创建时间
     */
    record Row(String id, Long userId, String author, Long orderId, Long windowId, int score,
               String content, String images, String tags, String requestKey, String requestHash,
               LocalDateTime createdAt) {
    }

    /**
     * 评分统计投影。
     *
     * @param reviewCount  可见评价条数
     * @param averageScore 平均分，无评价时为 null
     */
    record Rating(long reviewCount, Double averageScore) {
    }
}
