package com.hbue.ordering.order.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.order.model.OrderTimeoutOutboxDO;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper
public interface OrderTimeoutOutboxMapper extends BaseMapper<OrderTimeoutOutboxDO> {
    @Select("SELECT event_id,order_id,expires_at FROM order_timeout_outbox WHERE published_at IS NULL AND next_attempt_at<=CURRENT_TIMESTAMP ORDER BY next_attempt_at,event_id LIMIT 50")
    List<OrderTimeoutOutboxDO> pending();
    @Update("UPDATE order_timeout_outbox SET published_at=CURRENT_TIMESTAMP,last_error=NULL WHERE event_id=#{id} AND published_at IS NULL")
    int sent(@Param("id") String id);
    @Update("UPDATE order_timeout_outbox SET attempts=attempts+1,last_error=#{error},next_attempt_at=TIMESTAMPADD(SECOND,LEAST(300,POW(2,LEAST(attempts,8))),CURRENT_TIMESTAMP) WHERE event_id=#{id} AND published_at IS NULL")
    int failed(@Param("id") String id,@Param("error") String error);
    @Update("UPDATE order_timeout_outbox SET dead_letter_at=CURRENT_TIMESTAMP WHERE event_id=#{id}")
    int deadLetter(@Param("id") String id);
}
