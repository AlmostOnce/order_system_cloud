package com.hbue.ordering.order.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.order.model.OrderDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 订单主表数据访问接口。
 *
 * <p>负责按用户、幂等键读取订单，以及执行带原状态和版本号条件的原子状态更新；
 * 该接口只处理数据库访问，不承担订单权限判断或状态迁移规则。</p>
 *
 * @author order-system
 * @date 2026-10-14
 */
@Mapper
public interface OrderMapper extends BaseMapper<OrderDO> {
    @Select("SELECT * FROM orders WHERE order_no = #{orderNo} LIMIT 1")
    OrderDO selectByOrderNo(@Param("orderNo") String orderNo);

    /** 唯一生成列只覆盖仍有效的取餐码，完成或取消后自动释放。 */
    @Select("SELECT * FROM orders WHERE active_pickup_code = #{code}")
    OrderDO selectByActivePickupCode(@Param("code") String code);

    @Update("""
        UPDATE orders SET pickup_code = #{code}
        WHERE order_id = #{orderId} AND user_id = #{userId}
          AND status IN ('已支付','制作中','待取货')
          AND (pickup_code IS NULL OR pickup_code = '')
          AND EXISTS(SELECT 1 FROM order_items i WHERE i.order_id=orders.order_id AND i.quantity>i.refund_approved_quantity)
        """)
    int assignPickupCodeIfMissing(@Param("orderId") Long orderId, @Param("userId") Long userId, @Param("code") String code);

    @Update("""
        UPDATE orders SET status = '已完成', pickup_code = NULL, completed_at = COALESCE(completed_at,CURRENT_TIMESTAMP),
            updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE order_id = #{orderId} AND status = '待取货'
          AND version = #{version} AND pickup_code = #{code}
          AND EXISTS(SELECT 1 FROM order_items i WHERE i.order_id=orders.order_id AND i.quantity>i.refund_approved_quantity)
        """)
    int completeWithPickupCode(@Param("orderId") Long orderId, @Param("version") Integer version, @Param("code") String code);

    /**
     * 按本人范围、状态组和主键游标查询订单头，单次读取上限由Service限定为limit+1。
     * order_id创建后不再变化，严格小于游标使顶部新建订单不会挤动后续分页；不使用深OFFSET。
     * @param userId 已认证用户内部编号，始终进入WHERE，不能用客户端传入的用户替代
     * @param statuses 服务端白名单映射的中文状态；null表示全部，禁止传空列表
     * @param beforeId 本人已验证的订单游标；null表示最新一页
     * @param size Service已限制的取数上限，包含用于判断hasMore的额外一条
     * @return 按订单编号倒序排列的有界结果，不加载订单明细
     */
    @Select("""
        <script>
        SELECT * FROM orders WHERE user_id = #{userId}
        <if test="statuses != null">
          AND status IN
          <foreach collection="statuses" item="status" open="(" separator="," close=")">#{status}</foreach>
        </if>
        <if test="beforeId != null">AND order_id &lt; #{beforeId}</if>
        ORDER BY order_id DESC LIMIT #{size}
        </script>
        """)
    List<OrderDO> selectUserPage(@Param("userId") Long userId, @Param("statuses") List<String> statuses,
            @Param("beforeId") Long beforeId, @Param("size") int size);

    /**
     * 校验游标订单是否属于本人；不存在与他人订单都返回false，不暴露其他顾客的信息。
     * 此处不要求游标仍匹配旧状态，避免订单状态正常流转后阻断本人继续向后翻页。
     */
    @Select("SELECT EXISTS(SELECT 1 FROM orders WHERE user_id=#{userId} AND order_id=#{orderId})")
    boolean existsForUserOrder(@Param("userId") Long userId, @Param("orderId") Long orderId);

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

    /**
     * 使用旧状态和乐观锁版本号原子更新订单状态。
     *
     * @param orderId 订单 ID
     * @param expectedStatus 读取时的原状态
     * @param expectedVersion 读取时的版本号
     * @param targetStatus 目标状态
     * @return 更新行数；未匹配到原状态或版本时返回 0
     */
    @Update("""
            UPDATE orders
            SET status = #{targetStatus},
                updated_at = CURRENT_TIMESTAMP,
                version = version + 1
            WHERE order_id = #{orderId}
              AND status = #{expectedStatus}
              AND version = #{expectedVersion}
              AND (#{targetStatus} NOT IN ('制作中','待取货') OR EXISTS(
                  SELECT 1 FROM order_items i WHERE i.order_id=orders.order_id AND i.quantity>i.refund_approved_quantity))
            """)
    int updateStatusIfVersionMatches(
            @Param("orderId") Long orderId,
            @Param("expectedStatus") String expectedStatus,
            @Param("expectedVersion") Integer expectedVersion,
            @Param("targetStatus") String targetStatus
    );

    /** 与接单/核销共用订单行及版本，申请按获取锁后的真实状态决定是否直接退款。 */
    @Select("SELECT * FROM orders WHERE order_id=#{id} FOR UPDATE")
    OrderDO lockRefundOrder(@Param("id") Long id);
    /** 退款占用或释放菜品后递增版本，使之前读取旧内容的接单请求竞争失败。 */
    @Update("UPDATE orders SET version=version+1 WHERE order_id=#{id}")
    int bumpRefundVersion(@Param("id") Long id);
    /** 所有份数已实际退款才结束未完成订单；已完成保留核销及评价历史，不能被改回未履约。 */
    @Update("""
        UPDATE orders SET status='已退款',pickup_code=NULL,version=version+1,updated_at=CURRENT_TIMESTAMP
        WHERE order_id=#{id} AND status IN ('已支付','制作中','待取货')
          AND NOT EXISTS(SELECT 1 FROM order_items i WHERE i.order_id=orders.order_id AND i.quantity>i.refunded_quantity)
        """)
    int finishRefundedOrder(@Param("id") Long id);
}
