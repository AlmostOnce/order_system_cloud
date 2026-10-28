package com.hbue.ordering.order.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.hbue.ordering.order.ranking.SalesOutboxRow;
import java.util.List;

@Mapper
public interface OrderNotificationOutboxMapper {
    @Insert("""
        INSERT INTO order_notification_outbox(event_id,order_id,order_version,payload)
        VALUES(#{eventId},#{orderId},#{orderVersion},#{payload})
        """)
    int insert(@Param("eventId") String eventId, @Param("orderId") Long orderId,
            @Param("orderVersion") Integer orderVersion, @Param("payload") String payload);
    @Select("SELECT event_id,order_id,payload FROM order_notification_outbox WHERE published_at IS NULL AND next_attempt_at <= CURRENT_TIMESTAMP ORDER BY id LIMIT 50")
    List<SalesOutboxRow> pending();
    @Update("UPDATE order_notification_outbox SET published_at=CURRENT_TIMESTAMP,last_error=NULL WHERE event_id=#{id} AND published_at IS NULL")
    int markPublished(@Param("id") String id);
    @Update("UPDATE order_notification_outbox SET attempts=attempts+1,next_attempt_at=TIMESTAMPADD(SECOND,LEAST(300,POW(2,LEAST(attempts,8))),CURRENT_TIMESTAMP),last_error=#{error} WHERE event_id=#{id} AND published_at IS NULL")
    int retryLater(@Param("id") String id, @Param("error") String error);
}
