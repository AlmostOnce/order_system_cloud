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
