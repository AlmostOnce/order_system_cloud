package com.hbue.ordering.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.notification.model.NotificationDO;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface NotificationMapper extends BaseMapper<NotificationDO> {
    @Select("""
        <script>SELECT id,notification_id,notification_type,title,content,order_id,created_at,read_at
        FROM notifications WHERE recipient_id=#{userId}
        <if test="cursor != null">AND id &lt; (SELECT id FROM notifications WHERE notification_id=#{cursor} AND recipient_id=#{userId})</if>
        ORDER BY id DESC LIMIT #{size}</script>
        """)
    List<NotificationDO> page(@Param("userId") long userId, @Param("cursor") String cursor, @Param("size") int size);
    @Select("SELECT EXISTS(SELECT 1 FROM notifications WHERE recipient_id=#{userId} AND notification_id=#{id})")
    boolean existsForUser(@Param("userId") long userId, @Param("id") String id);
    @Select("SELECT COUNT(*) FROM notifications WHERE recipient_id=#{userId} AND read_at IS NULL")
    long unreadCount(@Param("userId") long userId);
    @Update("UPDATE notifications SET read_at=COALESCE(read_at,CURRENT_TIMESTAMP(3)) WHERE recipient_id=#{userId} AND notification_id=#{id}")
    int markRead(@Param("userId") long userId, @Param("id") String id);
    @Update("UPDATE notifications SET read_at=CURRENT_TIMESTAMP(3) WHERE recipient_id=#{userId} AND read_at IS NULL")
    int markAllRead(@Param("userId") long userId);
    @Insert("""
        INSERT INTO notifications(notification_id,recipient_id,event_id,notification_type,title,content,order_id)
        VALUES(#{id},#{userId},#{eventId},'ORDER_STATUS','订单状态更新',#{content},#{orderId})
        """)
    int insertOrderEvent(@Param("id") String id, @Param("userId") long userId, @Param("eventId") String eventId,
            @Param("orderId") long orderId, @Param("content") String content);
    @Select("SELECT EXISTS(SELECT 1 FROM notifications WHERE recipient_id=#{userId} AND event_id=#{eventId})")
    boolean existsEvent(@Param("userId") long userId, @Param("eventId") String eventId);
}
