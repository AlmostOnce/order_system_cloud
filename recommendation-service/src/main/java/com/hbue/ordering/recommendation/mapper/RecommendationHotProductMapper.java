package com.hbue.ordering.recommendation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.recommendation.model.RecommendationHotProductDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 热门榜本地快照读写；成功整批替换避免读到半张榜单。
 */
public interface RecommendationHotProductMapper extends BaseMapper<RecommendationHotProductDO> {
    /**
     * 清除旧版本榜单；必须和新榜插入处于同一事务。
     */
    @Delete("DELETE FROM recommendation_hot_products")
    int deleteAll();

    /**
     * 写入由统计服务提供的聚合榜单行。
     */
    @Insert("""
            INSERT INTO recommendation_hot_products(product_id,window_id,sales,rank_no,updated_at)
            VALUES(#{productId},#{windowId},#{sales},#{rankNo},#{updatedAt})
            """)
    int insertRankingItem(RecommendationHotProductDO item);

    /**
     * 榜单只需50项；查询结果不包含顾客或订单信息。
     */
    @Select("SELECT * FROM recommendation_hot_products ORDER BY rank_no,product_id LIMIT #{limit}")
    List<RecommendationHotProductDO> selectTop(@Param("limit") int limit);
}
