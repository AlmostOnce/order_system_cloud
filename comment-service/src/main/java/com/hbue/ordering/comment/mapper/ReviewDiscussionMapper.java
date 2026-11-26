package com.hbue.ordering.comment.mapper;

import com.hbue.ordering.comment.model.ReviewDiscussionDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 评价评论的读写。
 *
 * <p>读取默认由调用方按 {@code status = 1} 过滤：撤回与屏蔽采用软删除，正文留在库里
 * 便于追溯，但已不可见的评论不能出现在列表、计数与预览中。幂等重放与权限判断需要
 * 读取"任意状态"的记录，因此 {@link #byId(String)} 与 {@link #byRequest(Long, String)}
 * 刻意不带状态条件。</p>
 *
 * <p>所有语句只操作 {@code review_discussion} 单表，不跨库、不关联评价表，
 * 档口编号来自写入时冗余的 {@code window_id}。</p>
 *
 * @author order-system
 * @date 2026-11-25
 */
@Mapper
public interface ReviewDiscussionMapper {

    /**
     * 返回评论的全部列，供内部投影使用。
     */
    String COLUMNS = "discussion_id, review_type, review_id, window_id, root_id, parent_id, depth, "
            + "author_id, author_role, author_name, content, status, version, request_key, created_at, updated_at";

    /**
     * 写入一条评论。
     *
     * <p>并发首提由唯一键 {@code uk_review_discussion_request(author_id, request_key)} 兜底，
     * 调用方捕获冲突后按请求键回读赢家。</p>
     *
     * @param row 评论持久化对象
     * @return 受影响行数
     */
    @Insert("""
            INSERT INTO review_discussion(discussion_id, review_type, review_id, window_id, root_id, parent_id,
                depth, author_id, author_role, author_name, content, status, version, request_key, created_at)
            VALUES(#{row.discussionId}, #{row.reviewType}, #{row.reviewId}, #{row.windowId}, #{row.rootId},
                #{row.parentId}, #{row.depth}, #{row.authorId}, #{row.authorRole}, #{row.authorName},
                #{row.content}, #{row.status}, #{row.version}, #{row.requestKey}, #{row.createdAt})
            """)
    int insert(@Param("row") ReviewDiscussionDO row);

    /**
     * 按公开标识读取评论，包含已撤回与已屏蔽记录。
     *
     * <p>用于回复前定位楼层根与父评论、编辑前读取当前版本。是否需要"必须已发布"
     * 由服务层按场景判断，SQL 不替业务做决定。</p>
     *
     * @param id 公开评论标识
     * @return 评论行；不存在时返回 null
     */
    @Select("SELECT " + COLUMNS + " FROM review_discussion WHERE discussion_id = #{id}")
    ReviewDiscussionDO byId(@Param("id") String id);

    /**
     * 按作者与幂等请求键读取评论，包含已撤回与已屏蔽记录。
     *
     * <p>不带状态过滤：重复提交时如果原记录已撤回，必须能读到它并返回冲突，
     * 而不是让客户端以为可以"重新创建"。</p>
     *
     * @param authorId   评论人内部用户编号
     * @param requestKey 客户端幂等请求键
     * @return 评论行；不存在时返回 null
     */
    @Select("SELECT " + COLUMNS + " FROM review_discussion "
            + "WHERE author_id = #{authorId} AND request_key = #{requestKey}")
    ReviewDiscussionDO byRequest(@Param("authorId") Long authorId,
                                 @Param("requestKey") String requestKey);

    /**
     * 读取某条评价下的顶层评论（楼层），按内部主键倒序，最新在前。
     *
     * <p>游标是上一页最后一条的公开标识，服务端换算成内部主键后做范围查询；
     * 子查询再次限定同一评价与顶层状态，避免跨评价或跨楼层的游标被误用。</p>
     *
     * @param reviewType 评价类型编码
     * @param reviewId   评价公开标识
     * @param before     上一页最后一条的公开评论标识，null 表示第一页
     * @param size       取数上限，调用方传入 limit + 1 用于判断是否还有下一页
     * @return 顶层评论列表
     */
    @Select("""
            SELECT discussion_id, review_type, review_id, window_id, root_id, parent_id, depth,
                   author_id, author_role, author_name, content, status, version, request_key, created_at, updated_at
            FROM review_discussion
            WHERE review_type = #{reviewType}
              AND review_id = #{reviewId}
              AND depth = 1
              AND status = 1
              AND (#{before} IS NULL OR id < (
                  SELECT cursor_row.id FROM review_discussion cursor_row
                  WHERE cursor_row.discussion_id = #{before}
                    AND cursor_row.review_type = #{reviewType}
                    AND cursor_row.review_id = #{reviewId}
                    AND cursor_row.depth = 1
                    AND cursor_row.status = 1
              ))
            ORDER BY id DESC
            LIMIT #{size}
            """)
    List<ReviewDiscussionDO> pageRoots(@Param("reviewType") String reviewType,
                                       @Param("reviewId") String reviewId,
                                       @Param("before") String before,
                                       @Param("size") int size);

    /**
     * 读取某楼层内的可见回复，按内部主键正序，符合对话发生顺序。
     *
     * @param rootId 楼层根评论标识
     * @param after  上一页最后一条的公开评论标识，null 表示第一页
     * @param size   取数上限，调用方传入 limit + 1
     * @return 楼层内回复列表
     */
    @Select("""
            SELECT discussion_id, review_type, review_id, window_id, root_id, parent_id, depth,
                   author_id, author_role, author_name, content, status, version, request_key, created_at, updated_at
            FROM review_discussion
            WHERE root_id = #{rootId}
              AND depth = 2
              AND status = 1
              AND (#{after} IS NULL OR id > (
                  SELECT cursor_row.id FROM review_discussion cursor_row
                  WHERE cursor_row.discussion_id = #{after}
                    AND cursor_row.root_id = #{rootId}
                    AND cursor_row.depth = 2
                    AND cursor_row.status = 1
              ))
            ORDER BY id ASC
            LIMIT #{size}
            """)
    List<ReviewDiscussionDO> pageReplies(@Param("rootId") String rootId,
                                         @Param("after") String after,
                                         @Param("size") int size);

    /**
     * 批量统计多条评价的顶层评论数。
     *
     * <p>评价列表一次最多 50 条，必须批量查询；逐条计数会产生 50 次往返。</p>
     *
     * @param reviewType 评价类型编码
     * @param reviewIds  评价公开标识集合，非空
     * @return 每条评价对应的楼层数
     */
    @Select("""
            <script>
            SELECT review_id AS reviewId, COUNT(*) AS total
            FROM review_discussion
            WHERE review_type = #{reviewType}
              AND depth = 1
              AND status = 1
              AND review_id IN
              <foreach collection="reviewIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY review_id
            </script>
            """)
    List<ReviewCount> countRootsByReviews(@Param("reviewType") String reviewType,
                                          @Param("reviewIds") List<String> reviewIds);

    /**
     * 批量读取多条评价各自最新的若干条顶层评论，用于列表预览。
     *
     * <p>用窗口函数按评价分组取前 N 条，避免把整页评价的全部评论读进内存；
     * 单条热门评价的评论数不可控，内存裁剪会放大查询成本。</p>
     *
     * @param reviewType 评价类型编码
     * @param reviewIds  评价公开标识集合，非空
     * @param limit      每条评价最多返回的预览条数
     * @return 预览评论列表，按评价与倒序编号排列
     */
    @Select("""
            <script>
            SELECT discussion_id, review_type, review_id, window_id, root_id, parent_id, depth,
                   author_id, author_role, author_name, content, status, version, request_key, created_at, updated_at
            FROM (
                SELECT discussion_id, review_type, review_id, window_id, root_id, parent_id, depth,
                       author_id, author_role, author_name, content, status, version, request_key, created_at, updated_at,
                       ROW_NUMBER() OVER (PARTITION BY review_id ORDER BY id DESC) AS rn
                FROM review_discussion
                WHERE review_type = #{reviewType}
                  AND depth = 1
                  AND status = 1
                  AND review_id IN
                  <foreach collection="reviewIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            ) ranked
            WHERE ranked.rn &lt;= #{limit}
            ORDER BY ranked.review_id, ranked.rn
            </script>
            """)
    List<ReviewDiscussionDO> previewRoots(@Param("reviewType") String reviewType,
                                          @Param("reviewIds") List<String> reviewIds,
                                          @Param("limit") int limit);

    /**
     * 批量读取多个楼层各自最早的若干条可见回复，用于楼层列表的对话预览。
     *
     * <p>按正序取前 N 条：预览用于让读者看到对话如何开始，而不是最新几句。
     * 完整回复仍通过楼层回复接口分页读取。</p>
     *
     * @param rootIds 楼层根评论标识集合，非空
     * @param limit   每个楼层最多返回的预览条数
     * @return 预览回复列表
     */
    @Select("""
            <script>
            SELECT discussion_id, review_type, review_id, window_id, root_id, parent_id, depth,
                   author_id, author_role, author_name, content, status, version, request_key, created_at, updated_at
            FROM (
                SELECT discussion_id, review_type, review_id, window_id, root_id, parent_id, depth,
                       author_id, author_role, author_name, content, status, version, request_key, created_at, updated_at,
                       ROW_NUMBER() OVER (PARTITION BY root_id ORDER BY id ASC) AS rn
                FROM review_discussion
                WHERE depth = 2
                  AND status = 1
                  AND root_id IN
                  <foreach collection="rootIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            ) ranked
            WHERE ranked.rn &lt;= #{limit}
            ORDER BY ranked.root_id, ranked.rn
            </script>
            """)
    List<ReviewDiscussionDO> previewRepliesByRoots(@Param("rootIds") List<String> rootIds,
                                                   @Param("limit") int limit);

    /**
     * 批量统计多个楼层下的可见回复数。
     *
     * @param rootIds 楼层根评论标识集合，非空
     * @return 每个楼层的回复数
     */
    @Select("""
            <script>
            SELECT root_id AS rootId, COUNT(*) AS total
            FROM review_discussion
            WHERE depth = 2
              AND status = 1
              AND root_id IN
              <foreach collection="rootIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY root_id
            </script>
            """)
    List<RootCount> countRepliesByRoots(@Param("rootIds") List<String> rootIds);

    /**
     * 批量读取被回复评论的作者与状态，用于拼装"回复 @某某"。
     *
     * <p>父评论可能不在当前页，必须按标识批量回查，不能逐条查。</p>
     *
     * @param ids 父评论公开标识集合，非空
     * @return 父评论的作者快照与状态
     */
    @Select("""
            <script>
            SELECT discussion_id AS discussionId, author_name AS authorName, status
            FROM review_discussion
            WHERE discussion_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<ParentRef> findParentRefs(@Param("ids") List<String> ids);

    /**
     * 编辑评论正文。
     *
     * <p>条件更新同时要求作者本人、已发布与版本号一致；返回 0 表示评论已被撤回、
     * 已被屏蔽或版本已变化，调用方必须返回冲突而不是当作成功。</p>
     *
     * @param id       公开评论标识
     * @param authorId 作者内部用户编号
     * @param version  客户端看到的版本号
     * @param content  新正文
     * @return 受影响行数
     */
    @Update("""
            UPDATE review_discussion
            SET content = #{content}, version = version + 1, updated_at = NOW(3)
            WHERE discussion_id = #{id} AND author_id = #{authorId} AND status = 1 AND version = #{version}
            """)
    int updateContent(@Param("id") String id,
                      @Param("authorId") Long authorId,
                      @Param("version") int version,
                      @Param("content") String content);

    /**
     * 作者撤回自己的评论（软删除）。
     *
     * <p>只允许从已发布状态撤回；已被管理员屏蔽的记录不能被作者覆盖，
     * 否则屏蔽事实会被抹掉。</p>
     *
     * @param id       公开评论标识
     * @param authorId 作者内部用户编号
     * @return 受影响行数
     */
    @Update("""
            UPDATE review_discussion
            SET status = 2, version = version + 1, updated_at = NOW(3)
            WHERE discussion_id = #{id} AND author_id = #{authorId} AND status = 1
            """)
    int revoke(@Param("id") String id, @Param("authorId") Long authorId);

    /**
     * 管理员屏蔽评论。
     *
     * <p>幂等：只更新已发布的记录，重复屏蔽返回 0，由服务层按"已屏蔽"处理。
     * 屏蔽不删除正文，保留追责能力。</p>
     *
     * @param id 公开评论标识
     * @return 受影响行数
     */
    @Update("""
            UPDATE review_discussion
            SET status = 3, version = version + 1, updated_at = NOW(3)
            WHERE discussion_id = #{id} AND status = 1
            """)
    int hide(@Param("id") String id);

    /**
     * 单条评价的可见楼层数。
     *
     * @param reviewType 评价类型编码
     * @param reviewId   评价公开标识
     * @return 楼层数
     */
    @Select("SELECT COUNT(*) FROM review_discussion "
            + "WHERE review_type = #{reviewType} AND review_id = #{reviewId} AND depth = 1 AND status = 1")
    int countRoots(@Param("reviewType") String reviewType, @Param("reviewId") String reviewId);

    /**
     * 评价维度的楼层计数投影。
     *
     * @param reviewId 评价公开标识
     * @param total    楼层数
     */
    record ReviewCount(String reviewId, int total) {
    }

    /**
     * 楼层维度的回复计数投影。
     *
     * @param rootId 楼层根评论标识
     * @param total  可见回复数
     */
    record RootCount(String rootId, int total) {
    }

    /**
     * 父评论引用投影，用于展示"回复 @某某"。
     *
     * @param discussionId 父评论公开标识
     * @param authorName   父评论作者展示名快照
     * @param status       父评论状态，已撤回或已屏蔽时前端展示占位文案
     */
    record ParentRef(String discussionId, String authorName, int status) {
    }
}
