package com.hbue.ordering.recommendation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.recommendation.model.RecommendationSearchIntentDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDateTime;
import java.util.List;

/** 主动搜索短期信号访问接口，按用户和 queryId 防止分页及网络重试重复计数。 */
public interface RecommendationSearchIntentMapper extends BaseMapper<RecommendationSearchIntentDO> {
    /** 幂等写入一条成功的首屏查询，唯一键冲突表示该次意图已保存。 */
    @Insert("""
        INSERT IGNORE INTO recommendation_search_intents
          (user_id,query_id,keyword,restaurant_id,window_id,category_id,ranked_product_ids,created_at)
        VALUES(#{userId},#{queryId},#{keyword},#{restaurantId},#{windowId},#{categoryId},
               CAST(#{rankedProductIds} AS JSON),#{createdAt})
        """)
    int insertIfAbsent(RecommendationSearchIntentDO intent);

    /** 只读本人近期、尚在保留期内的最多20条查询。 */
    @Select("""
        SELECT * FROM recommendation_search_intents
        WHERE user_id=#{userId} AND created_at>=#{since}
        ORDER BY created_at DESC,intent_id DESC LIMIT #{limit}
        """)
    List<RecommendationSearchIntentDO> selectRecentByUser(
            @Param("userId") Long userId, @Param("since") LocalDateTime since, @Param("limit") int limit);

    /** 完全相同关键词与筛选范围在十分钟内合并，避免反复提交抬高一次搜索信号。 */
    @Select("""
        SELECT intent_id FROM recommendation_search_intents
        WHERE user_id=#{userId} AND keyword=#{keyword}
          AND ((#{restaurantId} IS NULL AND restaurant_id IS NULL) OR restaurant_id=#{restaurantId})
          AND ((#{windowId} IS NULL AND window_id IS NULL) OR window_id=#{windowId})
          AND ((#{categoryId} IS NULL AND category_id IS NULL) OR category_id=#{categoryId})
          AND created_at>=#{since}
        ORDER BY created_at DESC,intent_id DESC LIMIT 1
        """)
    Long selectRecentDuplicate(@Param("userId") Long userId, @Param("keyword") String keyword,
            @Param("restaurantId") Long restaurantId, @Param("windowId") Long windowId,
            @Param("categoryId") Long categoryId, @Param("since") LocalDateTime since);

    /** 限制每天不同搜索意图数量；到上限后只停止采集，不影响用户当前搜索响应。 */
    @Select("SELECT COUNT(*) FROM recommendation_search_intents WHERE user_id=#{userId} AND created_at>=#{since}")
    long countSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    /** 到期搜索词及候选列表一并删除，避免短期意图超出约定保留期。 */
    @org.apache.ibatis.annotations.Delete("DELETE FROM recommendation_search_intents WHERE created_at < #{before}")
    int deleteExpired(@Param("before") LocalDateTime before);
}
