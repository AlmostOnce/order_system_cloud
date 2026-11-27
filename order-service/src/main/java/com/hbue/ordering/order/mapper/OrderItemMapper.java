package com.hbue.ordering.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.order.model.OrderItemDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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
     * 一次批量读取本页最多100个订单的推荐明细，避免每订单一次查询；空集合由服务层提前返回。
     * 只读推荐协议所需字段，不读取价格、取餐码等无关事实；逐订单和明细主键保证稳定顺序。
     */
    @Select("""
            <script>
            SELECT item_id, order_id, product_id, selection_key_snapshot, specification_text_snapshot,
                   specification_selection_snapshot, quantity, refunded_quantity
            FROM order_items
            WHERE order_id IN
              <foreach collection="orderIds" item="orderId" open="(" separator="," close=")">#{orderId}</foreach>
            ORDER BY order_id ASC, item_id ASC
            </script>
            """)
    List<OrderItemDO> selectRecommendationItems(@Param("orderIds") List<Long> orderIds);


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

    /**
     * 将券优惠分摊写入指定订单明细；原价、订单归属和金额守恒条件均由 SQL 再次核对。
     */
    @Update("""
            UPDATE order_items
            SET line_discount_amount = #{discountAmount}, line_payable_amount = #{payableAmount}
            WHERE item_id = #{itemId} AND order_id = #{orderId}
              AND line_amount = #{grossAmount} AND line_discount_amount = 0
              AND #{discountAmount} >= 0 AND #{discountAmount} <= line_amount
              AND #{payableAmount} = line_amount - #{discountAmount}
            """)
    int applyCouponSnapshot(@Param("itemId") Long itemId, @Param("orderId") Long orderId,
                            @Param("grossAmount") java.math.BigDecimal grossAmount,
                            @Param("discountAmount") java.math.BigDecimal discountAmount,
                            @Param("payableAmount") java.math.BigDecimal payableAmount);

    /**
     * 原子占用可申请份数；批准的未接单退款同时扣除可履约份数，仍由订单行锁保护状态分支。
     */
    @Update("UPDATE order_items SET refund_reserved_quantity=refund_reserved_quantity+#{quantity},refund_approved_quantity=refund_approved_quantity+#{approved} WHERE item_id=#{id} AND order_id=#{orderId} AND quantity-refund_reserved_quantity>=#{quantity}")
    int reserveRefund(@Param("id") Long id, @Param("orderId") Long orderId, @Param("quantity") int quantity, @Param("approved") int approved);

    /**
     * 审批只把已占用份数转为停止履约，不能超出申请量。调用方须先锁订单和退款单。
     */
    @Update("UPDATE order_items SET refund_approved_quantity=refund_approved_quantity+#{quantity} WHERE item_id=#{id} AND refund_approved_quantity+#{quantity}<=refund_reserved_quantity")
    int approveRefund(@Param("id") Long id, @Param("quantity") int quantity);

    /**
     * 撤回或经理最终拒绝时释放未批准额度，不能释放已经开始入账的份数。
     */
    @Update("UPDATE order_items SET refund_reserved_quantity=refund_reserved_quantity-#{quantity} WHERE item_id=#{id} AND refund_reserved_quantity-#{quantity}>=refund_approved_quantity")
    int releaseRefund(@Param("id") Long id, @Param("quantity") int quantity);

    /**
     * 只有远程资金及必要库存都确认后累计成功数量；重复执行由退款SUCCESS状态拦截。
     */
    @Update("UPDATE order_items SET refunded_quantity=refunded_quantity+#{quantity} WHERE item_id=#{id} AND refunded_quantity+#{quantity}<=refund_approved_quantity")
    int finishRefund(@Param("id") Long id, @Param("quantity") int quantity);
}
