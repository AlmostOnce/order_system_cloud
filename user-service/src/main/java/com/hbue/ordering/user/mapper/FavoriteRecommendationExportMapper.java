package com.hbue.ordering.user.mapper;

import com.hbue.ordering.user.model.FavoriteRecommendationExportRow;
import com.hbue.ordering.user.model.FavoriteRecommendationWatermarks;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

/** 推荐回填只读查询；分页读取收藏关系，不提供跨库访问或用户写入能力。 */
@Mapper
public interface FavoriteRecommendationExportMapper {
    /**
     * 在首屏的可重复读事务中读取两个可见最大值；发件箱最大值只提供观测信息。
     * 自增编号的分配顺序不等于不同关系的事务提交顺序，不能用该值过滤实时事件。
     *
     * @return 收藏分页上界及当前可见发件箱最大值；空表返回零
     */
    @Select("""
        SELECT (SELECT COALESCE(MAX(favorite_id), 0) FROM user_favorites) AS favoriteWatermark,
               (SELECT COALESCE(MAX(id), 0) FROM user_favorite_event_outbox) AS observedOutboxWatermark
        """)
    FavoriteRecommendationWatermarks readWatermarks();

    /**
     * 按收藏主键递增读取固定上界内的一页，同时读取每个关系的最新可见发件箱编号。
     *
     * <p>同一关系的新增、删除受唯一键及行锁串行化，变更与发件箱写入同事务；
     * 因此本关系可见的最新编号能作为自身版本。历史收藏尚无事件时返回 0。
     * 每页都是独立快照；读取缺失的关系不能推断为取消，实时事件必须完整消费。</p>
     *
     * @param afterFavoriteId 上页末条收藏编号，首屏为零
     * @param watermark 首屏固定的收藏编号上界
     * @param limit 查询条数，最多 101；额外一条用于判断下一页
     * @return 按收藏编号递增的记录及逐关系版本
     */
    @Select("""
        SELECT f.favorite_id AS favoriteId, f.user_id AS userId, f.target_type AS targetType,
               f.target_id AS targetId, f.created_at AS createdAt,
               COALESCE((SELECT o.id FROM user_favorite_event_outbox o
                         WHERE o.user_id = f.user_id AND o.target_type = f.target_type
                           AND o.target_id = f.target_id ORDER BY o.id DESC LIMIT 1), 0) AS sourceVersion
        FROM user_favorites f
        WHERE f.favorite_id > #{afterFavoriteId} AND f.favorite_id <= #{watermark}
        ORDER BY f.favorite_id ASC LIMIT #{limit}
        """)
    List<FavoriteRecommendationExportRow> selectPage(@Param("afterFavoriteId") long afterFavoriteId,
                                                     @Param("watermark") long watermark,
                                                     @Param("limit") int limit);
}
