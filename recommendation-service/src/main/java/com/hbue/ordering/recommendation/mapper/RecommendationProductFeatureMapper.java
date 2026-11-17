package com.hbue.ordering.recommendation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.recommendation.model.RecommendationProductFeatureDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;

/** 商品目录本地读模型访问接口；批次状态仅在商品服务读取成功后更新。 */
public interface RecommendationProductFeatureMapper extends BaseMapper<RecommendationProductFeatureDO> {
    /** 查询符合当前首页范围的有限候选集合，避免每次推荐触达源商品数据库。 */
    @Select("""
        SELECT * FROM recommendation_product_features
        WHERE available=1 AND (#{restaurantId} IS NULL OR restaurant_id=#{restaurantId})
        ORDER BY sort_order,product_id LIMIT #{limit}
        """)
    List<RecommendationProductFeatureDO> selectAvailable(
            @Param("restaurantId") Long restaurantId, @Param("limit") int limit);

    /** 取本人购买过的有限商品特征，停用商品可用于形成历史画像但不会进入候选列表。 */
    @Select("""
        <script>
        SELECT * FROM recommendation_product_features WHERE product_id IN
        <foreach collection="productIds" item="id" open="(" separator="," close=")">#{id}</foreach>
        </script>
        """)
    List<RecommendationProductFeatureDO> selectByProductIds(@Param("productIds") List<Long> productIds);

    /** 目录刷新按商品ID覆盖当前展示字段，旧订单规格快照不随之修改。 */
    @Insert("""
        INSERT INTO recommendation_product_features
          (product_id,category_id,window_id,restaurant_id,window_name,category_name,product_name,description,
           image_url,base_price,sort_order,has_specifications,specification_groups,available)
        VALUES(#{productId},#{categoryId},#{windowId},#{restaurantId},#{windowName},#{categoryName},#{productName},
               #{description},#{imageUrl},#{basePrice},#{sortOrder},#{hasSpecifications},CAST(#{specificationGroups} AS JSON),1)
        ON DUPLICATE KEY UPDATE category_id=VALUES(category_id),window_id=VALUES(window_id),
          restaurant_id=VALUES(restaurant_id),window_name=VALUES(window_name),category_name=VALUES(category_name),
          product_name=VALUES(product_name),description=VALUES(description),image_url=VALUES(image_url),
          base_price=VALUES(base_price),sort_order=VALUES(sort_order),has_specifications=VALUES(has_specifications),
          specification_groups=VALUES(specification_groups),available=1
        """)
    int upsertDocument(RecommendationProductFeatureDO document);

    /** 成功完成整窗口快照后撤下未返回商品；失败批次不得调用，以免误清缓存。 */
    @Update("UPDATE recommendation_product_features SET available=0 WHERE window_id=#{windowId}")
    int markWindowUnavailable(@Param("windowId") Long windowId);

    /** 单商品刷新成功但源端已返回空集合时撤下本地旧候选，历史订单仍保留原始明细。 */
    @Update("UPDATE recommendation_product_features SET available=0 WHERE window_id=#{windowId} AND product_id=#{productId}")
    int markProductUnavailable(@Param("windowId") Long windowId, @Param("productId") Long productId);

    /** 成功完整快照后只将该窗口未出现在批次中的历史商品设为不可推荐。 */
    @Update("""
        <script>
        UPDATE recommendation_product_features SET available=0
        WHERE window_id=#{windowId} AND product_id NOT IN
        <foreach collection="productIds" item="id" open="(" separator="," close=")">#{id}</foreach>
        </script>
        """)
    int markMissingUnavailable(@Param("windowId") Long windowId, @Param("productIds") List<Long> productIds);

    /** 全量窗口列表读取成功后，撤下已停用/删除窗口下的旧候选。 */
    @Update("""
        <script>
        UPDATE recommendation_product_features SET available=0
        WHERE window_id NOT IN
        <foreach collection="windowIds" item="id" open="(" separator="," close=")">#{id}</foreach>
        </script>
        """)
    int markWindowsMissingUnavailable(@Param("windowIds") List<Long> windowIds);

    /** 全量目录确认当前没有有效窗口时撤下全部旧候选。 */
    @Update("UPDATE recommendation_product_features SET available=0")
    int markAllUnavailable();
}
