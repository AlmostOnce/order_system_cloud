package com.hbue.ordering.order.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 消费事实只读查询，供评价评论的准入门槛使用。
 *
 * <p>评论服务不保存订单数据，"是否买过某道菜/是否在某档口消费过"只能由订单服务回答。
 * 本 Mapper 只做 {@code EXISTS} 判断，不返回订单明细、金额或时间，避免跨服务调用
 * 顺带泄漏与准入无关的交易数据。</p>
 *
 * <p>判定口径固定为：订单属于该用户、状态为"已完成"、且至少还有一条未全额退款的明细。
 * 订单状态机的"已完成"是终态，完成后不能再退款，因此该状态即净消费；
 * 同时排除已完成订单中已被全额退款的行（历史部分退款场景），避免退款后仍能参与评论。</p>
 *
 * @author order-system
 * @date 2026-11-25
 */
@Mapper
public interface PurchaseFactMapper {

    /**
     * 判断用户是否购买过指定菜品。
     *
     * <p>只认本人订单：{@code user_id} 由评论服务从已认证身份解析后传入，
     * 不接受客户端指定。菜品维度要求订单明细中该菜品仍有未退款数量。</p>
     *
     * @param userId    用户内部编号
     * @param productId 菜品编号
     * @return 存在有效购买事实时返回 true
     */
    @Select("""
            SELECT EXISTS(
                SELECT 1 FROM orders o
                WHERE o.user_id = #{userId}
                  AND o.status = '已完成'
                  AND EXISTS(
                      SELECT 1 FROM order_items i
                      WHERE i.order_id = o.order_id
                        AND i.product_id = #{productId}
                        AND i.quantity > i.refund_approved_quantity
                  )
            )
            """)
    boolean existsCompletedProductPurchase(@Param("userId") Long userId,
                                           @Param("productId") Long productId);

    /**
     * 判断用户是否在指定档口消费过。
     *
     * <p>档口维度只要该订单还有任意一条未全额退款的明细即可，不要求具体菜品，
     * 与"在这家店吃过饭"的产品语义一致。</p>
     *
     * @param userId   用户内部编号
     * @param windowId 档口编号
     * @return 存在有效消费事实时返回 true
     */
    @Select("""
            SELECT EXISTS(
                SELECT 1 FROM orders o
                WHERE o.user_id = #{userId}
                  AND o.window_id = #{windowId}
                  AND o.status = '已完成'
                  AND EXISTS(
                      SELECT 1 FROM order_items i
                      WHERE i.order_id = o.order_id
                        AND i.quantity > i.refund_approved_quantity
                  )
            )
            """)
    boolean existsCompletedWindowPurchase(@Param("userId") Long userId,
                                          @Param("windowId") Long windowId);
}
