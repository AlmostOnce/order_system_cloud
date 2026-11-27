package com.hbue.ordering.stock.messaging;

import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 库存事件 outbox 持久化与 Seata 全局事务结果门控。
 */
@Mapper
public interface StockEventOutboxMapper {

    @Insert("""
            INSERT INTO stock_event_outbox(
                event_id, biz_type, biz_ref, stock_id, window_id, product_id,
                available_qty, stock_version, xid
            ) VALUES (
                #{row.eventId}, #{row.bizType}, #{row.bizRef}, #{row.stockId},
                #{row.windowId}, #{row.productId}, #{row.availableQty},
                #{row.stockVersion}, #{xid}
            )
            """)
    int insert(@Param("row") StockEventOutboxRow row, @Param("xid") String xid);

    /**
     * 只选取可以安全外发的事件。Seata AT 分支仍未完成全局决策时，undo_log 仍存在；
     * 全局提交清理 undo_log 后库存与 outbox 保留，全局回滚则库存变更和 outbox 一并撤销。
     */
    @Select("""
            SELECT o.event_id, o.biz_type, o.biz_ref, o.stock_id, o.window_id, o.product_id,
                   o.available_qty, o.stock_version, o.attempts, o.created_at
            FROM stock_event_outbox o
            WHERE o.published_at IS NULL
              AND o.next_attempt_at <= CURRENT_TIMESTAMP
              AND (o.xid IS NULL OR NOT EXISTS (
                  SELECT 1 FROM undo_log u WHERE u.xid = o.xid
              ))
            ORDER BY o.next_attempt_at, o.created_at, o.event_id
            LIMIT 100
            """)
    List<StockEventOutboxRow> pending();

    /**
     * 原子领取一条待发事件的租约，避免多实例重复外发库存事件。
     *
     * <p>以"未发布且租约已到期"为 CAS 谓词把 {@code next_attempt_at} 推进到未来。并发实例在同一轮
     * 扫描到同一行时，InnoDB 行锁使 UPDATE 串行，只有第一个实例得到 1，其余得到 0 后跳过，
     * 从而保证同一事件不会被同秒投递两次。持有租约的实例若在发送后崩溃，租约到期后其它实例可重领，
     * 消息仍是至少一次语义，消费端按事件号幂等。</p>
     *
     * @param eventId 事件号
     * @param seconds 租约秒数，应大于一次同步发送的最长耗时
     * @return 1 表示本实例获得租约；0 表示已被其它实例领取或已发布
     */
    @Update("""
            UPDATE stock_event_outbox
            SET next_attempt_at = TIMESTAMPADD(SECOND, #{seconds}, CURRENT_TIMESTAMP)
            WHERE event_id = #{eventId} AND published_at IS NULL AND next_attempt_at <= CURRENT_TIMESTAMP
            """)
    int lease(@Param("eventId") String eventId, @Param("seconds") int seconds);

    @Update("""
            UPDATE stock_event_outbox
            SET published_at = CURRENT_TIMESTAMP, last_error = NULL
            WHERE event_id = #{eventId} AND published_at IS NULL
            """)
    int markPublished(@Param("eventId") String eventId);

    @Update("""
            UPDATE stock_event_outbox
            SET next_attempt_at = TIMESTAMPADD(
                    SECOND, LEAST(300, POW(2, LEAST(attempts, 8))), CURRENT_TIMESTAMP
                ),
                attempts = attempts + 1,
                last_error = #{error}
            WHERE event_id = #{eventId} AND published_at IS NULL
            """)
    int retryLater(@Param("eventId") String eventId, @Param("error") String error);
}
