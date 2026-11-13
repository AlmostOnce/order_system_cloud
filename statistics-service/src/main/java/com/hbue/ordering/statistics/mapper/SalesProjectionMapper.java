package com.hbue.ordering.statistics.mapper;

import com.hbue.ordering.statistics.model.SalesEventDO;
import com.hbue.ordering.statistics.model.SalesEventItemDO;
import com.hbue.ordering.statistics.model.SalesProductTotalDO;
import com.hbue.ordering.statistics.model.SalesWindowTotalDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 销量事实和汇总读模型的数据访问层。
 *
 * <p>所有写操作只访问 statistics_db；订单事实由上游消息/内部契约提供，本 Mapper 不连接其他服务数据库。</p>
 */
@Mapper
public interface SalesProjectionMapper {
    /** 以两个唯一键原子判重；返回0表示该订单事实已在本地保存。 */
    @Insert("""
        INSERT IGNORE INTO statistics_sales_events (event_id, order_id, window_id, paid_at, event_version)
        VALUES (#{eventId}, #{orderId}, #{windowId}, #{paidAt}, #{eventVersion})
        """)
    int insertEventIfAbsent(SalesEventDO event);

    /** 查询既有事件供重复消息校验，避免同一稳定键携带不同事实时被静默吞掉。 */
    @Select("SELECT event_id, order_id, window_id, paid_at, event_version, created_at FROM statistics_sales_events WHERE event_id=#{eventId}")
    SalesEventDO selectEvent(@Param("eventId") String eventId);

    /** 先把事件头写成功，再在同一事务内批量保存其全部菜品快照。 */
    @Insert("""
        <script>
        INSERT INTO statistics_sales_event_items (event_id, item_id, product_id, product_name, quantity)
        VALUES
        <foreach collection="items" item="item" separator=",">
          (#{item.eventId}, #{item.itemId}, #{item.productId}, #{item.productName}, #{item.quantity})
        </foreach>
        </script>
        """)
    int insertEventItems(@Param("items") List<SalesEventItemDO> items);

    /** 按事件顺序读取已存明细，用于验证重复消息的完整载荷一致。 */
    @Select("SELECT event_id, item_id, product_id, product_name, quantity FROM statistics_sales_event_items WHERE event_id=#{eventId} ORDER BY item_id")
    List<SalesEventItemDO> selectEventItems(@Param("eventId") String eventId);

    /** 累加窗口周期份数；调用方仅在事件头首次插入时执行。 */
    @Insert("""
        INSERT INTO statistics_sales_window_totals (period_type, period_key, window_id, paid_quantity)
        VALUES (#{periodType}, #{periodKey}, #{windowId}, #{quantity})
        ON DUPLICATE KEY UPDATE paid_quantity = paid_quantity + VALUES(paid_quantity)
        """)
    int addWindowTotal(@Param("periodType") String periodType, @Param("periodKey") String periodKey,
                       @Param("windowId") Long windowId, @Param("quantity") long quantity);

    /** 累加菜品周期份数，并只让更晚的订单明细更新展示名称。 */
    @Insert("""
        INSERT INTO statistics_sales_product_totals
            (period_type, period_key, window_id, product_id, product_name, latest_item_id, paid_quantity)
        VALUES (#{periodType}, #{periodKey}, #{windowId}, #{productId}, #{productName}, #{latestItemId}, #{quantity})
        ON DUPLICATE KEY UPDATE
            product_name = IF(VALUES(latest_item_id) > latest_item_id, VALUES(product_name), product_name),
            latest_item_id = GREATEST(latest_item_id, VALUES(latest_item_id)),
            paid_quantity = paid_quantity + VALUES(paid_quantity)
        """)
    int addProductTotal(@Param("periodType") String periodType, @Param("periodKey") String periodKey,
                        @Param("windowId") Long windowId, @Param("productId") Long productId,
                        @Param("productName") String productName, @Param("latestItemId") Long latestItemId,
                        @Param("quantity") long quantity);

    /** 事件事实、明细及汇总已提交后递增版本，让缓存消费者发现有新的权威投影。 */
    @Update("UPDATE statistics_sales_projection_meta SET version=version+1 WHERE projection_id=1")
    int incrementProjectionVersion();

    /** 读取当前已提交投影版本，用于发现 Redis 是否落后于 MySQL。 */
    @Select("SELECT version FROM statistics_sales_projection_meta WHERE projection_id=1")
    Long selectProjectionVersion();

    /** Redis 丢失或版本落后时读取累计榜的完整权威汇总。 */
    @Select("""
        SELECT period_type, period_key, window_id, product_id, product_name, latest_item_id, paid_quantity
        FROM statistics_sales_product_totals
        WHERE period_type='ALL' AND period_key='ALLTIME'
        ORDER BY product_id, window_id
        """)
    List<SalesProductTotalDO> selectAllTimeProductTotals();

    /** 重建 Redis 订单去重集合时使用全部已持久化订单号。 */
    @Select("SELECT order_id FROM statistics_sales_events ORDER BY order_id")
    List<Long> selectAllEventOrderIds();

    /** 查询一个自然月内各窗口总份数；月份由服务按 Asia/Shanghai 计算后传入。 */
    @Select("""
        SELECT window_id, paid_quantity
        FROM statistics_sales_window_totals
        WHERE period_type='MONTH' AND period_key=#{periodKey}
        ORDER BY window_id
        """)
    List<SalesWindowTotalDO> selectMonthlyWindowTotals(@Param("periodKey") String periodKey);

    /** 查询单一窗口的月汇总，避免点餐页读取全部窗口后再在 Java 层筛选。 */
    @Select("SELECT window_id, paid_quantity FROM statistics_sales_window_totals WHERE period_type='MONTH' AND period_key=#{periodKey} AND window_id=#{windowId}")
    SalesWindowTotalDO selectMonthlyWindowTotal(@Param("periodKey") String periodKey, @Param("windowId") Long windowId);

    /** 查询一个窗口当前自然月的菜品份数，只返回确实有销量的菜品。 */
    @Select("""
        SELECT window_id, product_id, product_name, latest_item_id, paid_quantity
        FROM statistics_sales_product_totals
        WHERE period_type='MONTH' AND period_key=#{periodKey} AND window_id=#{windowId}
        ORDER BY paid_quantity DESC, product_id ASC
        """)
    List<SalesProductTotalDO> selectMonthlyProductTotals(@Param("periodKey") String periodKey,
                                                         @Param("windowId") Long windowId);
}
