package com.hbue.ordering.recommendation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.recommendation.model.RecommendationFavoriteStateDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 当前收藏状态投影访问接口；版本大小按数据库BIGINT数值判断。
 */
public interface RecommendationFavoriteStateMapper extends BaseMapper<RecommendationFavoriteStateDO> {
    /**
     * 查询本人当前激活收藏；取消墓碑不参与算法，但保留在数据库压制旧事件。
     */
    @Select("SELECT * FROM recommendation_favorite_states WHERE user_id=#{userId} AND active=1")
    List<RecommendationFavoriteStateDO> selectActiveByUser(@Param("userId") Long userId);

    /**
     * 新版本覆盖当前状态，旧消息、重放和相同版本重试均不改变新状态。
     */
    @org.apache.ibatis.annotations.Insert("""
            INSERT INTO recommendation_favorite_states(user_id,target_type,target_id,source_version,active,changed_at)
            VALUES(#{userId},#{targetType},#{targetId},#{sourceVersion},#{active},#{changedAt})
            ON DUPLICATE KEY UPDATE
              active=IF(VALUES(source_version)>source_version,VALUES(active),active),
              changed_at=IF(VALUES(source_version)>source_version,VALUES(changed_at),changed_at),
              source_version=GREATEST(source_version,VALUES(source_version))
            """)
    int upsertIfNewer(RecommendationFavoriteStateDO state);
}
