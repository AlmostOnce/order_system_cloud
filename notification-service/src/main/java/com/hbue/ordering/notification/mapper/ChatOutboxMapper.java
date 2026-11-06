package com.hbue.ordering.notification.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.notification.model.ChatOutboxDO;
import org.apache.ibatis.annotations.*;
import java.util.List;
/**
 * 聊天事务事件投递表访问。仅取到期未发布记录；成功记录投递时间，失败保留并推迟重试。
 */
@Mapper
public interface ChatOutboxMapper extends BaseMapper<ChatOutboxDO> {
    /**
     * 分批读取到期未投递事件。批量上限限制单轮数据库与Redis负担，投递失败记录保留以便重试。
     */
    @Select("SELECT id,event_uuid,conversation_uuid,attempts FROM chat_event_outbox WHERE delivered_at IS NULL AND next_attempt_at <= CURRENT_TIMESTAMP(3) ORDER BY id LIMIT 50")
    List<ChatOutboxDO> pending();
    /**
     * 记录已向在线提示总线发布。此时间仅代表发布动作完成，不代表对方在线或已经阅读。
     */
    @Update("UPDATE chat_event_outbox SET delivered_at=CURRENT_TIMESTAMP(3) WHERE id=#{id} AND delivered_at IS NULL")
    int delivered(@Param("id") long id);
    /**
     * 保留失败事件并递增尝试次数，延后10秒再次尝试；重复提示通过消息UUID和补查去重吸收。
     */
    @Update("UPDATE chat_event_outbox SET attempts=attempts+1,next_attempt_at=DATE_ADD(CURRENT_TIMESTAMP(3),INTERVAL 10 SECOND) WHERE id=#{id} AND delivered_at IS NULL")
    int retry(@Param("id") long id);
}
