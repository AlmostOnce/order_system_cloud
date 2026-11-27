package com.hbue.ordering.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.product.model.DiningWindowDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 窗口表的数据访问接口。
 *
 * <p>继承 MyBatis-Plus 的基础 CRUD 能力，供商品服务读取和维护档口持久化数据；
 * 本接口只负责数据库访问，不处理操作者的数据范围或菜单缓存失效。</p>
 *
 * @author order-system
 * @date 2026-09-28
 */
public interface DiningWindowMapper extends BaseMapper<DiningWindowDO> {

    /**
     * 批量查询指定餐厅下的全部档口 ID。
     *
     * <p>仅供服务间内部调用：user-service 在计算“餐厅经理可授予范围的候选账号”时，
     * 需要把经理持有的餐厅范围展开成档口集合。这里不筛选营业状态，因为停用档口的
     * 负责人账号仍可能需要被经理检索并调整范围。调用方必须保证餐厅集合非空，避免
     * 生成 `IN ()` 非法 SQL。</p>
     *
     * @param restaurantIds 餐厅 ID 集合，调用方保证非空且数量有界
     * @return 这些餐厅下的档口 ID 列表
     */
    @Select("""
            <script>
            SELECT window_id FROM dining_windows
            WHERE restaurant_id IN
            <foreach collection="restaurantIds" item="restaurantId" open="(" separator="," close=")">
                #{restaurantId}
            </foreach>
            </script>
            """)
    List<Long> selectIdsByRestaurantIds(@Param("restaurantIds") List<Long> restaurantIds);
}
