package com.hbue.ordering.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.order.model.OrderItemDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 订单菜品明细数据访问接口。
 *
 * @author order-system
 * @date 2026-09-28
 */
@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItemDO> {

    /**
     * 按订单 ID 查询明细，并按主键顺序返回。
     *
     * @param orderId 订单 ID
     * @return 订单明细列表
     */
    @Select("""
        SELECT *
        FROM order_items
        WHERE order_id = #{orderId}
        ORDER BY item_id ASC
        """)
    List<OrderItemDO> selectByOrderId(
            @Param("orderId") Long orderId
    );
}
