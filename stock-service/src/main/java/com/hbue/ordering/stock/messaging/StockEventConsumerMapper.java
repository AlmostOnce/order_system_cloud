package com.hbue.ordering.stock.messaging;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 库存事件消费幂等记录。 */
@Mapper
public interface StockEventConsumerMapper {
    @Select("SELECT COUNT(*) FROM stock_event_consumed WHERE event_id = #{eventId}")
    int countByEventId(@Param("eventId") String eventId);

    @Insert("""
            INSERT IGNORE INTO stock_event_consumed(event_id, processed_at)
            VALUES (#{eventId}, CURRENT_TIMESTAMP)
            """)
    int markConsumed(@Param("eventId") String eventId);
}
