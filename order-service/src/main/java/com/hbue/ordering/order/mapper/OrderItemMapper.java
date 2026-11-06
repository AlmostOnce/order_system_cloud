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

    /** 原子占用可申请份数；批准的未接单退款同时扣除可履约份数，仍由订单行锁保护状态分支。 */
    @Update("UPDATE order_items SET refund_reserved_quantity=refund_reserved_quantity+#{quantity},refund_approved_quantity=refund_approved_quantity+#{approved} WHERE item_id=#{id} AND order_id=#{orderId} AND quantity-refund_reserved_quantity>=#{quantity}")
    int reserveRefund(@Param("id") Long id,@Param("orderId") Long orderId,@Param("quantity") int quantity,@Param("approved") int approved);
    /** 审批只把已占用份数转为停止履约，不能超出申请量。调用方须先锁订单和退款单。 */
    @Update("UPDATE order_items SET refund_approved_quantity=refund_approved_quantity+#{quantity} WHERE item_id=#{id} AND refund_approved_quantity+#{quantity}<=refund_reserved_quantity")
    int approveRefund(@Param("id") Long id,@Param("quantity") int quantity);
    /** 撤回或经理最终拒绝时释放未批准额度，不能释放已经开始入账的份数。 */
    @Update("UPDATE order_items SET refund_reserved_quantity=refund_reserved_quantity-#{quantity} WHERE item_id=#{id} AND refund_reserved_quantity-#{quantity}>=refund_approved_quantity")
    int releaseRefund(@Param("id") Long id,@Param("quantity") int quantity);
    /** 只有远程资金及必要库存都确认后累计成功数量；重复执行由退款SUCCESS状态拦截。 */
    @Update("UPDATE order_items SET refunded_quantity=refunded_quantity+#{quantity} WHERE item_id=#{id} AND refunded_quantity+#{quantity}<=refund_approved_quantity")
    int finishRefund(@Param("id") Long id,@Param("quantity") int quantity);
}
