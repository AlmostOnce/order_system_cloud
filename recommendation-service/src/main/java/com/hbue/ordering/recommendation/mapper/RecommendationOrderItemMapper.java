package com.hbue.ordering.recommendation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.recommendation.model.RecommendationOrderItemDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 推荐订单明细快照访问接口；itemId 唯一保留成功退款的准确归属。热门销量统一由统计服务提供，此处不重复聚合。
 */
public interface RecommendationOrderItemMapper extends BaseMapper<RecommendationOrderItemDO> {
    /**
     * 删除旧版完整快照明细；必须在订单头锁和版本判断之后、同一事务内调用。
     */
    @Delete("DELETE FROM recommendation_order_items WHERE order_id=#{orderId}")
    int deleteByOrderId(@Param("orderId") Long orderId);

    /**
     * 一次读取多张订单的规格行，空订单集合由业务层提前拦截。
     */
    @Select("""
            <script>
            SELECT * FROM recommendation_order_items WHERE order_id IN
            <foreach collection="orderIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            ORDER BY order_id,item_id
            </script>
            """)
    List<RecommendationOrderItemDO> selectByOrderIds(@Param("orderIds") List<Long> orderIds);

}
