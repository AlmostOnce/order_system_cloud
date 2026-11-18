package com.hbue.ordering.user.mapper;

import com.hbue.ordering.user.messaging.FavoriteEventOutboxRow;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 收藏变更本地发件箱；关系与事件必须使用同一用户库事务。
 */
@Mapper
public interface FavoriteEventOutboxMapper {
    /**
     * 登记真实收藏或取消；由数据库分配单调编号，并通过收藏唯一键的行锁串行化同一关系。
     *
     * @param userId     已认证用户的内部编号
     * @param targetType PRODUCT 或 WINDOW
     * @param targetId   商品或窗口编号
     * @param active     变更后的状态，false 是取消墓碑
     * @return 写入行数，写入失败应使收藏事务回滚
     */
    @Insert("INSERT INTO user_favorite_event_outbox(user_id, target_type, target_id, active) VALUES(#{userId}, #{targetType}, #{targetId}, #{active})")
    int insert(@Param("userId") Long userId, @Param("targetType") String targetType,
               @Param("targetId") Long targetId, @Param("active") boolean active);

    /**
     * 每次读取最多 100 条到期事件，多实例可重复发送，消费端必须按 eventId 幂等。
     */
    @Select("SELECT id, user_id AS userId, target_type AS targetType, target_id AS targetId, active, changed_at AS changedAt FROM user_favorite_event_outbox WHERE published_at IS NULL AND next_attempt_at <= CURRENT_TIMESTAMP(3) ORDER BY next_attempt_at, id LIMIT 100")
    List<FavoriteEventOutboxRow> pending();

    /**
     * 仅 Broker 确认接收后标记；重复确认不覆盖已完成记录。
     */
    @Update("UPDATE user_favorite_event_outbox SET published_at=CURRENT_TIMESTAMP(3), last_error=NULL WHERE id=#{id} AND published_at IS NULL")
    int markPublished(@Param("id") Long id);

    /**
     * 失败记录有限长度的异常类型并指数退避；未确认事件保留直到成功。
     */
    @Update("UPDATE user_favorite_event_outbox SET next_attempt_at=TIMESTAMPADD(SECOND, LEAST(300, POW(2, LEAST(attempts, 8))), CURRENT_TIMESTAMP(3)), attempts=attempts+1, last_error=#{error} WHERE id=#{id} AND published_at IS NULL")
    int retryLater(@Param("id") Long id, @Param("error") String error);
}
