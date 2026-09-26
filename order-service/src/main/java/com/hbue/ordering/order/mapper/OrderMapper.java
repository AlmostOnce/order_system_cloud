package com.hbue.ordering.order.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.order.model.OrderDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderMapper extends BaseMapper<OrderDO> {

    /**
     * 查询指定用户的订单。
     *
     * @param userId 用户 ID
     * @return 用户订单列表
     */
    @Select("""
        SELECT *
        FROM orders
        WHERE user_id = #{userId}
        ORDER BY created_at DESC
        """)
    List<OrderDO> selectByUserId(
            @Param("userId") Long userId
    );

    /**
     * 按用户和幂等键查询已创建的订单。
     *
     * @param userId 用户 ID
     * @param idempotencyKey 幂等键
     * @return 已存在的订单；不存在时返回 null
     */
    @Select("""
        SELECT *
        FROM orders
        WHERE user_id = #{userId}
          AND idempotency_key = #{idempotencyKey}
        LIMIT 1
        """)
    OrderDO selectByUserIdAndIdempotencyKey(
            @Param("userId") Long userId,
            @Param("idempotencyKey") String idempotencyKey
    );
}
