package com.hbue.ordering.notification.mapper;

import com.hbue.ordering.notification.model.SystemConversationDO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统通知会话持久化。
 *
 * <p>唯一键 (customer_id, conversation_type) 保证每个用户每类系统会话只有一行：
 * 创建使用“冲突时只同步标题”的语义，保留已有摘要；推进摘要使用覆盖更新，
 * 并发写入不会产生重复会话，也不会把历史预览清空。</p>
 *
 * @author order-system
 * @date 2026-11-26
 */
@Mapper
public interface SystemConversationMapper {

    /**
     * 确保会话存在：首次打开消息页时创建订单中心会话，即使还没有通知也常驻。
     *
     * <p>唯一键冲突时只同步标题，不覆盖已有预览与时间，避免把历史摘要清空。</p>
     *
     * @param customerId       用户内部编号
     * @param conversationType 会话类型
     * @param title            会话名称
     * @return 受影响行数；冲突时 MySQL 返回 0 属于正常幂等结果
     */
    @Insert("""
            INSERT INTO system_conversations(customer_id,conversation_type,title)
            VALUES(#{customerId},#{conversationType},#{title})
            ON DUPLICATE KEY UPDATE title=VALUES(title)
            """)
    int ensure(@Param("customerId") long customerId, @Param("conversationType") String conversationType,
               @Param("title") String title);

    /**
     * 推进会话摘要与最新时间。通知写入与历史回填共用，重复执行结果一致。
     *
     * @param customerId       用户内部编号
     * @param conversationType 会话类型
     * @param title            会话名称
     * @param preview          最新通知摘要，空串表示暂无内容
     * @param latestAt         最新通知时间
     * @return 受影响行数
     */
    @Insert("""
            INSERT INTO system_conversations(customer_id,conversation_type,title,preview,latest_at)
            VALUES(#{customerId},#{conversationType},#{title},#{preview},#{latestAt})
            ON DUPLICATE KEY UPDATE title=VALUES(title),preview=VALUES(preview),latest_at=VALUES(latest_at)
            """)
    int advance(@Param("customerId") long customerId, @Param("conversationType") String conversationType,
                @Param("title") String title, @Param("preview") String preview,
                @Param("latestAt") LocalDateTime latestAt);

    /**
     * 查询用户的全部系统会话。
     *
     * <p>有通知的会话按时间倒序在前，尚无通知的常驻会话排在最后，
     * 与聊天会话列表“最近活跃优先”的阅读习惯一致。</p>
     *
     * @param customerId 用户内部编号
     * @return 会话列表
     */
    @Select("""
            SELECT * FROM system_conversations
            WHERE customer_id=#{customerId}
            ORDER BY latest_at IS NULL, latest_at DESC, conversation_id DESC
            """)
    List<SystemConversationDO> listByCustomer(@Param("customerId") long customerId);

    /**
     * 读取指定类型的会话，用于判断是否需要从历史通知回填摘要。
     *
     * @param customerId       用户内部编号
     * @param conversationType 会话类型
     * @return 会话实体，不存在时返回 null
     */
    @Select("SELECT * FROM system_conversations WHERE customer_id=#{customerId} AND conversation_type=#{conversationType}")
    SystemConversationDO find(@Param("customerId") long customerId,
                              @Param("conversationType") String conversationType);
}
