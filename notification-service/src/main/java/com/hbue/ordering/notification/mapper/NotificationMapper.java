package com.hbue.ordering.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.notification.model.NotificationDO;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 系统通知MyBatis-Plus数据访问。列表、未读与事件去重均由SQL绑定接收人范围，避免在内存中过滤他人数据。
 */
@Mapper
public interface NotificationMapper extends BaseMapper<NotificationDO> {
    /**
     * 按接收人和内部主键倒序分页；子查询再次绑定接收人范围。Service预先验证游标可见性，传入size+1判断后续页。
     */
    @Select("""
            SELECT id,notification_id,notification_type,title,content,order_id,biz_type,biz_id,created_at,read_at
            FROM notifications WHERE recipient_id=#{userId}
              AND (#{cursor} IS NULL OR id < (SELECT id FROM notifications
                  WHERE notification_id=#{cursor} AND recipient_id=#{userId}))
            ORDER BY id DESC LIMIT #{size}
            """)
    List<NotificationDO> page(@Param("userId") long userId, @Param("cursor") String cursor, @Param("size") int size);

    /**
     * 检查公开通知是否属于指定认证用户，用于隐藏他人通知的存在性。
     */
    @Select("SELECT EXISTS(SELECT 1 FROM notifications WHERE recipient_id=#{userId} AND notification_id=#{id})")
    boolean existsForUser(@Param("userId") long userId, @Param("id") String id);

    /**
     * 查询当前用户尚未阅读且未被通知偏好关闭的系统通知数。
     *
     * <p>关闭某个分类后，该分类的历史未读仍保留在消息中心，但不再计入红点；
     * 重新开启后未读会重新计入，因为阅读状态没有被修改。这里用 NOT EXISTS 按
     * 通知类型映射到偏好分类，未知类型不参与映射，保持默认提醒。</p>
     */
    @Select("""
            SELECT COUNT(*) FROM notifications n
            WHERE n.recipient_id=#{userId} AND n.read_at IS NULL
              AND NOT EXISTS (
                SELECT 1 FROM notification_preferences p
                WHERE p.user_id=#{userId} AND p.enabled=0
                  AND p.category=CASE n.notification_type
                        WHEN 'ORDER_STATUS' THEN 'ORDER'
                        WHEN 'SUPPORT_REPLY' THEN 'SUPPORT'
                        WHEN 'SUPPORT_STATUS' THEN 'SUPPORT'
                        WHEN 'COUPON_EXPIRING' THEN 'COUPON'
                        WHEN 'REVIEW_DISCUSSION' THEN 'REVIEW'
                        ELSE '' END)
            """)
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
     * 插入一条通用业务通知，接收用户与来源事件唯一键共同防止调用方重试产生重复通知。
     *
     * <p>支持反馈回复、状态变更等非订单场景，order_id 固定为空；业务引用写入
     * biz_type 与 biz_id，供消息中心按类型跳转到对应详情。调用方必须传入稳定的
     * 来源事件标识与公开业务标识，禁止写入内部主键。</p>
     *
     * @param id      公开通知标识
     * @param userId  接收用户内部编号
     * @param eventId 来源业务事件标识
     * @param type    通知类型
     * @param title   通知标题
     * @param content 通知内容
     * @param bizType 业务类型，如 SUPPORT
     * @param bizId   业务公开标识，可空
     * @return 受影响行数
     */
    @Insert("""
            INSERT INTO notifications(notification_id,recipient_id,event_id,notification_type,title,content,order_id,biz_type,biz_id)
            VALUES(#{id},#{userId},#{eventId},#{type},#{title},#{content},NULL,#{bizType},#{bizId})
            """)
    int insertEvent(@Param("id") String id, @Param("userId") long userId, @Param("eventId") String eventId,
                    @Param("type") String type, @Param("title") String title,
                    @Param("content") String content, @Param("bizType") String bizType,
                    @Param("bizId") String bizId);

    /**
     * 检查重复键是否确实来自同一接收人的同一来源事件，其他数据库冲突仍向上抛出。
     */
    @Select("SELECT EXISTS(SELECT 1 FROM notifications WHERE recipient_id=#{userId} AND event_id=#{eventId})")
    boolean existsEvent(@Param("userId") long userId, @Param("eventId") String eventId);
}
