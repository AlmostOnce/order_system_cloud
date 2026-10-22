package com.hbue.ordering.order.mapper;

import com.hbue.ordering.order.model.PaidSalesLine;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface SalesRankingMapper {
    /** 一个 SQL 一致性快照同时取销量和已计入订单 ID，重建后旧消息不会再次累加。 */
    @Select("""
        SELECT o.order_id, i.item_id, i.product_id, o.window_id,
               i.product_name_snapshot AS product_name, i.quantity
        FROM orders o JOIN order_items i ON i.order_id = o.order_id
        WHERE o.status IN ('已支付', '制作中', '待取货', '已完成')
        ORDER BY i.item_id
        """)
    @Options(timeout = 15)
    List<PaidSalesLine> allPaidLines();

    @Select("""
        SELECT o.order_id, i.item_id, i.product_id, o.window_id,
               i.product_name_snapshot AS product_name, i.quantity
        FROM orders o JOIN order_items i ON i.order_id = o.order_id
        WHERE o.order_id = #{orderId}
          AND o.status IN ('已支付', '制作中', '待取货', '已完成')
        ORDER BY i.item_id
        """)
    @Options(timeout = 5)
    List<PaidSalesLine> paidOrderLines(@Param("orderId") Long orderId);
}
