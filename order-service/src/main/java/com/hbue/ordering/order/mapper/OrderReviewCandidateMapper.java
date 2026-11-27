package com.hbue.ordering.order.mapper;

import com.hbue.ordering.order.vo.ReviewCandidateVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 待评价统计使用的只读查询。
 *
 * <p>只读取已完成订单及其明细编号，供评论服务判断“店铺评价或任一菜品评价是否缺失”。
 * 这里不做分页游标：调用方只需要最近的有限批订单做数量统计，按订单号倒序截断即可。
 * 查询不返回金额、用户信息或退款细节，避免内部接口扩大数据暴露面。</p>
 *
 * @author order-system
 * @date 2026-11-26
 */
@Mapper
public interface OrderReviewCandidateMapper {

    /**
     * 读取用户最近若干已完成订单的明细编号。
     *
     * <p>先用派生表截取最近 {@code limit} 个订单，再与明细连接，避免 LIMIT 作用在明细行上
     * 导致同一订单只返回部分菜品；MySQL 不允许在 IN 子查询中直接写 LIMIT，
     * 因此这里使用派生表而不是 {@code IN (SELECT ... LIMIT ...)}。</p>
     *
     * <p>订单状态按库内中文值 {@code 已完成} 过滤，与消费事实查询保持同一口径；
     * 同一订单的多条明细按明细主键升序返回，便于调用方稳定聚合。</p>
     *
     * @param userId 用户内部编号
     * @param limit  最多截取的订单数，由调用方限制在合理范围
     * @return 订单明细候选行，没有已完成订单时返回空集合
     */
    @Select("""
            SELECT o.order_id AS orderId, o.window_id AS windowId, i.product_id AS productId
            FROM orders o
            JOIN order_items i ON i.order_id = o.order_id
            JOIN (
                SELECT recent.order_id
                FROM orders recent
                WHERE recent.user_id = #{userId}
                  AND recent.status = '已完成'
                ORDER BY recent.order_id DESC
                LIMIT #{limit}
            ) recent_orders ON recent_orders.order_id = o.order_id
            ORDER BY o.order_id DESC, i.item_id ASC
            """)
    List<ReviewCandidateVO> selectCompletedOrderItems(@Param("userId") Long userId,
                                                      @Param("limit") int limit);
}
