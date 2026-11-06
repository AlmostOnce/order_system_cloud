package com.hbue.ordering.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.notification.model.NotificationDO;
import java.util.List;
import org.apache.ibatis.annotations.*;

/**
 * 系统通知MyBatis-Plus数据访问。列表、未读与事件去重均由SQL绑定接收人范围，避免在内存中过滤他人数据。
 */
@Mapper
public interface NotificationMapper extends BaseMapper<NotificationDO> {
    /**
     * 按接收人和内部主键倒序分页；子查询再次绑定接收人范围。Service预先验证游标可见性，传入size+1判断后续页。
     */
    @Select("""
        <script>SELECT id,notification_id,notification_type,title,content,order_id,created_at,read_at
        FROM notifications WHERE recipient_id=#{userId}
        <if test="cursor != null">AND id &lt; (SELECT id FROM notifications WHERE notification_id=#{cursor} AND recipient_id=#{userId})</if>
        ORDER BY id DESC LIMIT #{size}</script>
        """)
    List<NotificationDO> page(@Param("userId") long userId, @Param("cursor") String cursor, @Param("size") int size);
    /**
     * 检查公开通知是否属于指定认证用户，用于隐藏他人通知的存在性。
     */
    @Select("SELECT EXISTS(SELECT 1 FROM notifications WHERE recipient_id=#{userId} AND notification_id=#{id})")
    boolean existsForUser(@Param("userId") long userId, @Param("id") String id);
    /**
     * 查询当前用户尚未阅读的系统通知数，与商家聊天未读独立存储，前端合并展示红点。
     */
    @Select("SELECT COUNT(*) FROM notifications WHERE recipient_id=#{userId} AND read_at IS NULL")
    long unreadCount(@Param("userId") long userId);
    /**
     * 确认通知属于本人后标记已读；不存在与他人通知统一拒绝，不让客户端指定接收人。
     */
    @Update("UPDATE notifications SET read_at=COALESCE(read_at,CURRENT_TIMESTAMP(3)) WHERE recipient_id=#{userId} AND notification_id=#{id}")
    int markRead(@Param("userId") long userId, @Param("id") String id);
    /**
     * 只更新当前认证用户的全部系统通知，不改变商家聊天的独立阅读水位。
     */
    @Update("UPDATE notifications SET read_at=CURRENT_TIMESTAMP(3) WHERE recipient_id=#{userId} AND read_at IS NULL")
    int markAllRead(@Param("userId") long userId);
    /**
     * 插入一条订单状态通知，接收用户与来源事件唯一键共同防止MQ重投产生重复通知。
     */
    @Insert("""
        INSERT INTO notifications(notification_id,recipient_id,event_id,notification_type,title,content,order_id)
        VALUES(#{id},#{userId},#{eventId},'ORDER_STATUS','订单状态更新',#{content},#{orderId})
        """)
    int insertOrderEvent(@Param("id") String id, @Param("userId") long userId, @Param("eventId") String eventId,
            @Param("orderId") long orderId, @Param("content") String content);
    /**
     * 检查重复键是否确实来自同一接收人的同一来源事件，其他数据库冲突仍向上抛出。
     */
    @Select("SELECT EXISTS(SELECT 1 FROM notifications WHERE recipient_id=#{userId} AND event_id=#{eventId})")
    boolean existsEvent(@Param("userId") long userId, @Param("eventId") String eventId);
}
