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
    /**
     * 内部推荐回填按主键正序读取已支付事实，paid_at非空包含后来全退或完成的订单。
     * limit由服务层限定为2至101用于多取一条，不使用OFFSET或状态标签推测是否付过款。
     */
    @Select("""
            SELECT order_id, user_id, window_id, version, paid_at
            FROM orders
            WHERE order_id > #{afterOrderId} AND paid_at IS NOT NULL
            ORDER BY order_id ASC LIMIT #{limit}
            """)
    List<OrderDO> selectRecommendationPurchasePage(@Param("afterOrderId") Long afterOrderId, @Param("limit") int limit);

    @Select("SELECT * FROM orders WHERE order_no = #{orderNo} LIMIT 1")
    OrderDO selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 唯一生成列只覆盖仍有效的取餐码，完成或取消后自动释放。
     */
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
     *
     * @param userId   已认证用户内部编号，始终进入WHERE，不能用客户端传入的用户替代
     * @param statuses 服务端白名单映射的中文状态；null表示全部，禁止传空列表
     * @param beforeId 本人已验证的订单游标；null表示最新一页
     * @param size     Service已限制的取数上限，包含用于判断hasMore的额外一条
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
     * 商家订单队列的游标分页查询。
     *
     * <p>窗口条件始终进入 WHERE，由 Service 先完成数据范围判定；状态为服务端白名单
     * 映射的中文值，null 表示全部。订单主键创建后不变，使用严格小于游标避免顶部新增
     * 订单挤动后续分页，不使用深偏移。单次读取上限由 Service 限定为 limit + 1。</p>
     *
     * @param windowId 已通过范围校验的档口 ID
     * @param statuses 服务端白名单状态；null 表示全部
     * @param beforeId 已校验属于该窗口的游标订单 ID；null 表示第一页
     * @param size     Service 限制的取数上限，包含用于判断 hasMore 的额外一条
     * @return 按订单编号倒序排列的订单头，不加载明细
     */
    @Select("""
            <script>
            SELECT * FROM orders WHERE window_id = #{windowId}
            <if test="statuses != null">
              AND status IN
              <foreach collection="statuses" item="status" open="(" separator="," close=")">#{status}</foreach>
            </if>
            <if test="beforeId != null">AND order_id &lt; #{beforeId}</if>
            ORDER BY order_id DESC LIMIT #{size}
            </script>
            """)
    List<OrderDO> selectWindowPage(@Param("windowId") Long windowId,
                                   @Param("statuses") List<String> statuses,
                                   @Param("beforeId") Long beforeId,
                                   @Param("size") int size);

    /**
     * 校验游标订单是否属于指定窗口；用于拒绝把其他窗口的订单号当作游标。
     *
     * <p>不校验状态，订单在翻页期间正常流转不应阻断继续翻页。</p>
     *
     * @param windowId 已校验数据范围的档口 ID
     * @param orderId  游标订单 ID
     * @return 订单属于该窗口时返回 true
     */
    @Select("SELECT EXISTS(SELECT 1 FROM orders WHERE window_id=#{windowId} AND order_id=#{orderId})")
    boolean existsForWindowOrder(@Param("windowId") Long windowId, @Param("orderId") Long orderId);

    /**
     * 按档口聚合支付口径的订单数与应付金额。
     *
     * <p>经营报表口径与统计服务一致：以首次支付时间 {@code paid_at} 为事实，退款不冲减
     * 历史销售额，因此不按状态过滤。{@code windowIds} 为 null 表示全平台；非 null 时由
     * Service 按操作者范围生成，绝不能接受客户端直接传入。</p>
     *
     * @param from      统计区间起点（含）
     * @param to        统计区间终点（不含）
     * @param windowIds 允许统计的档口 ID 集合；null 表示全平台
     * @return 每个档口的订单数与金额，按金额降序
     */
    @Select("""
            <script>
            SELECT o.window_id AS windowId,
                   COUNT(*) AS orderCount,
                   COALESCE(SUM(o.payable_amount), 0) AS grossAmount
            FROM orders o
            WHERE o.paid_at IS NOT NULL
              AND o.paid_at &gt;= #{from}
              AND o.paid_at &lt; #{to}
            <if test="windowIds != null">
              AND o.window_id IN
              <foreach collection="windowIds" item="windowId" open="(" separator="," close=")">#{windowId}</foreach>
            </if>
            GROUP BY o.window_id
            ORDER BY grossAmount DESC, o.window_id ASC
            </script>
            """)
    List<com.hbue.ordering.order.model.WindowSalesAggregate> selectSalesByWindow(
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to,
            @Param("windowIds") java.util.List<Long> windowIds
    );

    /**
     * 按档口聚合已支付订单的菜品原始份数。
     *
     * <p>份数来自订单明细快照，退款不冲减，与销售额同口径。与金额分开查询，
     * 避免订单头与明细连接后重复累计金额。</p>
     *
     * @param from      统计区间起点（含）
     * @param to        统计区间终点（不含）
     * @param windowIds 允许统计的档口 ID 集合；null 表示全平台
     * @return 每个档口的原始购买份数
     */
    @Select("""
            <script>
            SELECT o.window_id AS windowId,
                   COALESCE(SUM(i.quantity), 0) AS paidQuantity
            FROM orders o
            JOIN order_items i ON i.order_id = o.order_id
            WHERE o.paid_at IS NOT NULL
              AND o.paid_at &gt;= #{from}
              AND o.paid_at &lt; #{to}
            <if test="windowIds != null">
              AND o.window_id IN
              <foreach collection="windowIds" item="windowId" open="(" separator="," close=")">#{windowId}</foreach>
            </if>
            GROUP BY o.window_id
            </script>
            """)
    List<com.hbue.ordering.order.model.WindowSalesAggregate> selectSalesQuantityByWindow(
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to,
            @Param("windowIds") java.util.List<Long> windowIds
    );

    /**
     * 按用户和幂等键查询已创建的订单。
     *
     * @param userId         用户 ID
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
     * @param orderId         订单 ID
     * @param expectedStatus  读取时的原状态
     * @param expectedVersion 读取时的版本号
     * @param targetStatus    目标状态
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

    /**
     * 仅由支付确认链路执行待支付到已支付的 CAS 更新，并首次固定支付成功时间。
     *
     * <p>把 paid_at 与状态、版本号放在同一条 SQL 内，支付通知竞争时只有一个请求能写入；
     * 条件要求 paid_at 为空，避免重复或补偿请求覆盖已确认的原始支付时间。</p>
     *
     * @param orderId         订单主键
     * @param expectedVersion 已读取的订单版本
     * @param paidAt          支付库已记录的 success_time
     * @return 更新行数；只有状态、版本和时间约束均匹配时为1
     */
    @Update("""
            UPDATE orders
            SET status = '已支付',
                paid_at = #{paidAt},
                updated_at = CURRENT_TIMESTAMP,
                version = version + 1
            WHERE order_id = #{orderId}
              AND status = '待支付'
              AND version = #{expectedVersion}
              AND paid_at IS NULL
            """)
    int markPaidIfVersionMatches(
            @Param("orderId") Long orderId,
            @Param("expectedVersion") Integer expectedVersion,
            @Param("paidAt") java.time.LocalDateTime paidAt
    );

    /**
     * 仅允许待支付且尚未绑定优惠券的订单写入一次金额快照；SQL 再次核对原价、优惠与应付关系。
     * 条件不符时返回零行，调用方回滚券锁定，避免重复或越界的优惠结果覆盖订单事实。
     */
    @Update("""
            UPDATE orders
            SET user_coupon_id = #{userCouponId}, coupon_title_snapshot = #{couponTitle},
                discount_amount = #{discountAmount}, payable_amount = #{payableAmount},
                updated_at = CURRENT_TIMESTAMP
            WHERE order_id = #{orderId} AND status = '待支付' AND user_coupon_id IS NULL
              AND discount_amount = 0 AND total_amount = #{grossAmount}
              AND #{discountAmount} > 0 AND #{discountAmount} < total_amount
              AND #{payableAmount} = total_amount - #{discountAmount}
            """)
    int applyCouponSnapshot(@Param("orderId") Long orderId, @Param("userCouponId") Long userCouponId,
                            @Param("couponTitle") String couponTitle, @Param("grossAmount") java.math.BigDecimal grossAmount,
                            @Param("discountAmount") java.math.BigDecimal discountAmount,
                            @Param("payableAmount") java.math.BigDecimal payableAmount);

    /**
     * 与接单/核销共用订单行及版本，申请按获取锁后的真实状态决定是否直接退款。
     */
    @Select("SELECT * FROM orders WHERE order_id=#{id} FOR UPDATE")
    OrderDO lockRefundOrder(@Param("id") Long id);

    /**
     * 退款占用或释放菜品后递增版本，使之前读取旧内容的接单请求竞争失败。
     */
    @Update("UPDATE orders SET version=version+1 WHERE order_id=#{id}")
    int bumpRefundVersion(@Param("id") Long id);

    /**
     * 所有份数已实际退款才结束未完成订单；已完成保留核销及评价历史，不能被改回未履约。
     */
    @Update("""
            UPDATE orders SET status='已退款',pickup_code=NULL,version=version+1,updated_at=CURRENT_TIMESTAMP
            WHERE order_id=#{id} AND status IN ('已支付','制作中','待取货')
              AND NOT EXISTS(SELECT 1 FROM order_items i WHERE i.order_id=orders.order_id AND i.quantity>i.refunded_quantity)
            """)
    int finishRefundedOrder(@Param("id") Long id);

    /**
     * 统计账号注销前仍未结束的订单数量。
     *
     * <p>待支付、已支付、制作中、待取货都视为未完成，必须阻断注销；已退款、已完成和
     * 已取消属于终态，不阻断。只返回计数，不读取订单明细。</p>
     *
     * @param userId 用户 ID
     * @return 未完成订单数
     */
    @Select("""
            SELECT COUNT(*) FROM orders
            WHERE user_id = #{userId} AND status IN ('待支付','已支付','制作中','待取货')
            """)
    long countUnfinishedForAccountDeletion(@Param("userId") Long userId);
}
