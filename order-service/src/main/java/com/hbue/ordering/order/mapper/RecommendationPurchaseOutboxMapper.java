package com.hbue.ordering.order.mapper;

import com.hbue.ordering.order.recommendation.RecommendationPurchaseOutboxRow;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 推荐购买事件的订单库Outbox，唯一事件键和订单/版本唯一键共同约束一版事实只保存一次。
 * 派发采用至少一次语义，消费者必须按源版本幂等；不能通过提前标记已发布掩盖网络失败。
 */
@Mapper
public interface RecommendationPurchaseOutboxMapper {
    /**
     * 同事务插入完整快照；重复键不覆盖旧正文，写入器随后核对既有正文，防止同版本事实冲突。
     */
    @Insert("""
            INSERT INTO recommendation_purchase_outbox(event_id, order_id, order_version, payload)
            VALUES (#{eventId}, #{orderId}, #{orderVersion}, #{payload})
            ON DUPLICATE KEY UPDATE event_id = event_id
            """)
    int insertIfAbsent(@Param("eventId") String eventId, @Param("orderId") Long orderId,
                       @Param("orderVersion") Integer orderVersion, @Param("payload") String payload);

    /**
     * 回读不可变事件，核对重复写入没有把同版本关联到另一份购买事实。
     */
    @Select("SELECT event_id, order_id, order_version, payload FROM recommendation_purchase_outbox WHERE event_id=#{eventId}")
    RecommendationPurchaseOutboxRow find(@Param("eventId") String eventId);

    /**
     * 每次最多读取50行到期任务，避免消息故障导致无限扫描或一次加载全部积压。
     */
    @Select("""
            SELECT event_id, order_id, order_version, payload FROM recommendation_purchase_outbox
            WHERE published_at IS NULL AND next_attempt_at <= CURRENT_TIMESTAMP
            ORDER BY next_attempt_at, event_id LIMIT 50
            """)
    List<RecommendationPurchaseOutboxRow> pending();

    /**
     * 仅在Broker同步确认后标记发布；并发重复确认保持幂等，不修改正文。
     */
    @Update("""
            UPDATE recommendation_purchase_outbox SET published_at=CURRENT_TIMESTAMP, last_error=NULL
            WHERE event_id=#{eventId} AND published_at IS NULL
            """)
    int markPublished(@Param("eventId") String eventId);

    /**
     * 失败按指数退避、最多300秒后再试；只保存异常类型，不把个人购买快照或凭据写进错误列。
     */
    @Update("""
            UPDATE recommendation_purchase_outbox
            SET next_attempt_at=TIMESTAMPADD(SECOND,LEAST(300,POW(2,LEAST(attempts,8))),CURRENT_TIMESTAMP),
                attempts=attempts+1, last_error=#{error}
            WHERE event_id=#{eventId} AND published_at IS NULL
            """)
    int retryLater(@Param("eventId") String eventId, @Param("error") String error);
}
