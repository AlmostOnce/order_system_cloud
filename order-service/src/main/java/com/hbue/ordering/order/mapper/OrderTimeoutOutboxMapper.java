package com.hbue.ordering.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.order.model.OrderTimeoutOutboxDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface OrderTimeoutOutboxMapper extends BaseMapper<OrderTimeoutOutboxDO> {
    @Select("SELECT event_id,order_id,expires_at FROM order_timeout_outbox WHERE published_at IS NULL AND next_attempt_at<=CURRENT_TIMESTAMP ORDER BY next_attempt_at,event_id LIMIT 50")
    List<OrderTimeoutOutboxDO> pending();

    /**
     * 原子领取一条待发超时事件的租约，避免多实例重复投递。
     *
     * <p>CAS 谓词要求"仍未发布且租约已到期"。多个实例并发 UPDATE 同一行时由 InnoDB 行锁串行化，
     * 只有第一个实例得到 1 并把 {@code next_attempt_at} 推进到未来，其余实例得到 0 后跳过本次发送。
     * 持有者崩溃时租约到期后可被重新领取，因此仍是至少一次投递。</p>
     *
     * @param id      事件编号
     * @param seconds 租约秒数
     * @return 1 表示本实例获得租约；0 表示已被其它实例领取或已发布
     */
    @Update("""
            UPDATE order_timeout_outbox
            SET next_attempt_at = TIMESTAMPADD(SECOND, #{seconds}, CURRENT_TIMESTAMP)
            WHERE event_id = #{id} AND published_at IS NULL AND next_attempt_at <= CURRENT_TIMESTAMP
            """)
    int lease(@Param("id") String id, @Param("seconds") int seconds);

    @Update("UPDATE order_timeout_outbox SET published_at=CURRENT_TIMESTAMP,last_error=NULL WHERE event_id=#{id} AND published_at IS NULL")
    int sent(@Param("id") String id);

    @Update("UPDATE order_timeout_outbox SET attempts=attempts+1,last_error=#{error},next_attempt_at=TIMESTAMPADD(SECOND,LEAST(300,POW(2,LEAST(attempts,8))),CURRENT_TIMESTAMP) WHERE event_id=#{id} AND published_at IS NULL")
    int failed(@Param("id") String id, @Param("error") String error);

    @Update("UPDATE order_timeout_outbox SET dead_letter_at=CURRENT_TIMESTAMP WHERE event_id=#{id}")
    int deadLetter(@Param("id") String id);
}
