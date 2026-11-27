package com.hbue.ordering.order.mapper;

import com.hbue.ordering.order.ranking.SalesOutboxRow;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SalesOutboxMapper {
    @Insert("""
            INSERT INTO sales_event_outbox(event_id, order_id, payload)
            VALUES (#{eventId}, #{orderId}, #{payload})
            """)
    int insert(@Param("eventId") String eventId, @Param("orderId") Long orderId, @Param("payload") String payload);

    @Select("""
            SELECT event_id, order_id, payload FROM sales_event_outbox
            WHERE published_at IS NULL AND next_attempt_at <= CURRENT_TIMESTAMP
            ORDER BY next_attempt_at, event_id LIMIT 50
            """)
    List<SalesOutboxRow> pending();

    /**
     * 原子领取一条待发事件的租约。
     *
     * <p>把 {@code next_attempt_at} 推进到当前时间之后，并以前置条件"仍未发布且租约已到期"作为 CAS 谓词。
     * 多个实例在同一轮扫描到同一行时，InnoDB 行锁使这些 UPDATE 串行执行：只有第一个实例能把时间推进到
     * 未来并得到 1，其余实例在重新求值谓词时看到租约未到期而得到 0，从而放弃本次发送。这解决了
     * {@code pending()} 只做普通查询、两实例同秒各发一次的问题。</p>
     *
     * <p>租约到期后若仍未标记发布（例如持有租约的实例发送后崩溃），其它实例可再次领取并重发，
     * 消息保持至少一次投递语义；消费端必须继续按 {@code eventId} 幂等。</p>
     *
     * @param eventId 事件号
     * @param seconds 租约秒数，应大于一次同步发送的最长耗时
     * @return 1 表示本实例获得租约、可以发送；0 表示已被其它实例领取或已发布
     */
    @Update("""
            UPDATE sales_event_outbox
            SET next_attempt_at = TIMESTAMPADD(SECOND, #{seconds}, CURRENT_TIMESTAMP)
            WHERE event_id = #{eventId} AND published_at IS NULL AND next_attempt_at <= CURRENT_TIMESTAMP
            """)
    int lease(@Param("eventId") String eventId, @Param("seconds") int seconds);

    @Update("""
            UPDATE sales_event_outbox SET published_at = CURRENT_TIMESTAMP, last_error = NULL
            WHERE event_id = #{eventId} AND published_at IS NULL
            """)
    int markPublished(@Param("eventId") String eventId);

    @Update("""
            UPDATE sales_event_outbox
            SET next_attempt_at = TIMESTAMPADD(SECOND, LEAST(300, POW(2, LEAST(attempts, 8))), CURRENT_TIMESTAMP),
                attempts = attempts + 1, last_error = #{error}
            WHERE event_id = #{eventId} AND published_at IS NULL
            """)
    int retryLater(@Param("eventId") String eventId, @Param("error") String error);
}
