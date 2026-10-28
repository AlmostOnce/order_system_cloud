package com.hbue.ordering.product.mapper;

import com.hbue.ordering.product.messaging.ProductSearchOutboxRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;

/** 商品目录搜索索引刷新事件 outbox。 */
@Mapper
public interface ProductSearchOutboxMapper {
    @Insert("INSERT INTO product_search_outbox(event_id, window_id) VALUES(#{eventId}, #{windowId})")
    int insert(@Param("eventId") String eventId, @Param("windowId") Long windowId);

    @Select("SELECT event_id AS eventId, window_id AS windowId FROM product_search_outbox WHERE published_at IS NULL AND next_attempt_at <= CURRENT_TIMESTAMP ORDER BY next_attempt_at, created_at LIMIT 100")
    List<ProductSearchOutboxRow> pending();

    @Update("UPDATE product_search_outbox SET published_at=CURRENT_TIMESTAMP, last_error=NULL WHERE event_id=#{eventId} AND published_at IS NULL")
    int markPublished(@Param("eventId") String eventId);

    @Update("UPDATE product_search_outbox SET next_attempt_at=TIMESTAMPADD(SECOND, LEAST(300, POW(2, LEAST(attempts, 8))), CURRENT_TIMESTAMP), attempts=attempts+1, last_error=#{error} WHERE event_id=#{eventId} AND published_at IS NULL")
    int retryLater(@Param("eventId") String eventId, @Param("error") String error);

    @Delete("DELETE FROM product_search_outbox WHERE published_at IS NOT NULL AND published_at < TIMESTAMPADD(DAY, -30, CURRENT_TIMESTAMP) LIMIT 1000")
    int deleteOldPublished();
}
