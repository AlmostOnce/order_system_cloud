package com.hbue.ordering.order.mapper;

import com.hbue.ordering.order.service.CouponConsumeOutboxRow;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 订单服务本地优惠券核销 Outbox 的持久化访问接口。
 */
@Mapper
public interface CouponConsumeOutboxMapper {

    /**
     * 将核销事件与订单支付状态写在同一数据库事务中。
     *
     * @param eventId      稳定事件号，主键约束负责阻止同一支付订单重复写入
     * @param orderNo      订单业务编号，每单唯一
     * @param userCouponId 已绑定的持券记录 ID
     * @param payload      序列化后的消息事实快照
     * @return 新增行数；非 1 时调用方回滚支付状态
     */
    @Insert("""
            INSERT INTO order_coupon_consume_outbox(event_id, order_no, user_coupon_id, payload)
            VALUES (#{eventId}, #{orderNo}, #{userCouponId}, #{payload})
            """)
    int insert(@Param("eventId") String eventId, @Param("orderNo") String orderNo,
               @Param("userCouponId") Long userCouponId, @Param("payload") String payload);

    /**
     * 批量扫描到期未发送或尚未由券服务确认消费的事件；有界读取避免一轮调度长期占用数据库连接。
     *
     * @return 按下次重试时间排序的最多 50 条待发或待确认消息
     */
    @Select("""
            SELECT event_id AS eventId, order_no AS orderNo, user_coupon_id AS userCouponId, payload
            FROM order_coupon_consume_outbox
            WHERE status IN ('PENDING', 'PUBLISHED') AND next_attempt_at <= CURRENT_TIMESTAMP
            ORDER BY next_attempt_at, event_id
            LIMIT 50
            """)
    List<CouponConsumeOutboxRow> pending();

    /**
     * 记录代理接收并安排消费确认超时后的再次投递；这里只代表已进入 Broker，不能当作业务核销完成。
     *
     * @param eventId 已发布事件号
     * @return 条件更新行数；确认请求可能先于发送响应到达，因此允许保留 CONFIRMED 状态
     */
    @Update("""
            UPDATE order_coupon_consume_outbox
            SET status = IF(status = 'PENDING', 'PUBLISHED', status),
                published_at = COALESCE(published_at, CURRENT_TIMESTAMP),
                next_attempt_at = TIMESTAMPADD(SECOND, LEAST(300, POW(2, LEAST(attempts, 8))), CURRENT_TIMESTAMP),
                attempts = attempts + 1,
                last_error = NULL
            WHERE event_id = #{eventId} AND status IN ('PENDING', 'PUBLISHED', 'CONFIRMED')
            """)
    int markPublished(@Param("eventId") String eventId);

    /**
     * 发送或消费确认失败时指数退避并保留待处理状态，不能把已确认事件退回重试队列。
     *
     * @param eventId 失败的事件号
     * @param error   最近一次失败的简短摘要
     * @return 条件更新行数；状态已改变时为 0
     */
    @Update("""
            UPDATE order_coupon_consume_outbox
            SET next_attempt_at = TIMESTAMPADD(SECOND, LEAST(300, POW(2, LEAST(attempts, 8))), CURRENT_TIMESTAMP),
                attempts = attempts + 1, last_error = #{error}
            WHERE event_id = #{eventId} AND status IN ('PENDING', 'PUBLISHED')
            """)
    int retryLater(@Param("eventId") String eventId, @Param("error") String error);

    /**
     * 只在事件三元组与订单快照相符时确认券服务已完成本地核销事务。
     */
    @Update("""
            UPDATE order_coupon_consume_outbox
            SET status = 'CONFIRMED', confirmed_at = CURRENT_TIMESTAMP
            WHERE event_id = #{eventId} AND order_no = #{orderNo} AND user_coupon_id = #{userCouponId}
              AND status IN ('PENDING', 'PUBLISHED')
            """)
    int confirm(@Param("eventId") String eventId, @Param("orderNo") String orderNo,
                @Param("userCouponId") Long userCouponId);

    /**
     * 确认重放时允许已提交的同一事件返回幂等成功，其他身份组合不能确认。
     */
    @Select("""
            SELECT COUNT(*) FROM order_coupon_consume_outbox
            WHERE event_id = #{eventId} AND order_no = #{orderNo} AND user_coupon_id = #{userCouponId}
              AND status = 'CONFIRMED'
            """)
    int isConfirmed(@Param("eventId") String eventId, @Param("orderNo") String orderNo,
                    @Param("userCouponId") Long userCouponId);
}
