package com.hbue.ordering.recommendation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.recommendation.model.RecommendationSourceCursorDO;
import org.apache.ibatis.annotations.*;

/**
 * 推荐历史数据源的断点访问接口；推进只增不减，避免并行实例回退已完成游标。
 */
@Mapper
public interface RecommendationSourceCursorMapper extends BaseMapper<RecommendationSourceCursorDO> {
    /**
     * 服务首次启动时惰性建行；重复启动不清除已有位置和完成状态。
     */
    @Insert("""
            INSERT IGNORE INTO recommendation_source_cursors(source_name,cursor_value,upper_bound,completed)
            VALUES(#{sourceName},0,NULL,0)
            """)
    int ensureExists(@Param("sourceName") String sourceName);

    /**
     * 读取当前已持久化断点；缺失需当作本地结构或写入故障处理。
     */
    @Select("SELECT * FROM recommendation_source_cursors WHERE source_name=#{sourceName}")
    RecommendationSourceCursorDO select(@Param("sourceName") String sourceName);

    /**
     * 在整页数据全部幂等投影后推进断点；收藏扫描上界只设置一次且完成标记单向变化。
     */
    @Update("""
            UPDATE recommendation_source_cursors
            SET cursor_value=GREATEST(cursor_value,#{cursorValue}),
                upper_bound=COALESCE(upper_bound,#{upperBound}),
                completed=IF(#{completed},1,completed),
                updated_at=CURRENT_TIMESTAMP
            WHERE source_name=#{sourceName}
            """)
    int advance(@Param("sourceName") String sourceName,
                @Param("cursorValue") long cursorValue, @Param("upperBound") Long upperBound,
                @Param("completed") boolean completed);
}
