package com.hbue.ordering.notification.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.notification.model.ChatConversationDO;
import org.apache.ibatis.annotations.*;
import java.util.List;
/**
 * 订单会话持久化。SQL按顾客归属或当前直接授权窗口限制查询；唯一索引保证一笔订单至多一个会话。
 */
@Mapper
public interface ChatConversationMapper extends BaseMapper<ChatConversationDO> {
    /**
     * 按公开会话UUID读取存储记录。本查询本身不授权，调用方必须继续检查认证用户与订单范围。
     */
    @Select("SELECT * FROM chat_conversations WHERE conversation_uuid=#{uuid}")
    ChatConversationDO find(@Param("uuid") String uuid);
    /**
     * 以FOR UPDATE锁定指定会话。所有发送先取得同一会话行锁，保证seq递增和幂等判断串行进行。
     */
    @Select("SELECT * FROM chat_conversations WHERE conversation_uuid=#{uuid} FOR UPDATE")
    ChatConversationDO lock(@Param("uuid") String uuid);
    /**
     * 按订单寻找现有会话，避免用户每次点击都创建新会话。并发兜底仍由订单唯一索引负责。
     */
    @Select("SELECT * FROM chat_conversations WHERE order_id=#{orderId}")
    ChatConversationDO byOrder(@Param("orderId") long orderId);
    /**
     * 使用当前读读取并发插入后的会话。避免MySQL可重复读快照看不到另一个事务刚提交的唯一会话。
     */
    @Select("SELECT * FROM chat_conversations WHERE order_id=#{orderId} FOR UPDATE")
    ChatConversationDO byOrderCurrent(@Param("orderId") long orderId);
    /**
     * 插入订单会话，订单唯一键冲突时保留原行，不覆盖原会话UUID、顾客或窗口。
     */
    @Insert("""
      INSERT INTO chat_conversations(conversation_uuid,order_id,order_no,window_id,window_name,customer_id)
      VALUES(#{conversationUuid},#{orderId},#{orderNo},#{windowId},#{windowName},#{customerId})
      ON DUPLICATE KEY UPDATE order_id=order_id
      """)
    int create(ChatConversationDO row);
    /**
     * 随消息事务更新最后序号、摘要和服务端时间。失败时整个发送事务回滚，不能出现摘要指向未提交消息。
     */
    @Update("UPDATE chat_conversations SET last_seq=#{seq},last_content=#{content},last_message_at=CURRENT_TIMESTAMP(3) WHERE id=#{id}")
    int advance(@Param("id") long id,@Param("seq") long seq,@Param("content") String content);
    /**
     * 按顾客归属或店主直接窗口范围查询游标页。Service先校验游标可见性并传入size+1；同一时间以UUID作为次级排序键。
     */
    @Select("""
      <script>SELECT * FROM chat_conversations WHERE
      <choose><when test="customer">customer_id=#{userId}</when><otherwise>window_id IN
      <foreach collection="windows" item="w" open="(" separator="," close=")">#{w}</foreach></otherwise></choose>
      <if test="cursor != null">AND (COALESCE(last_message_at,created_at),conversation_uuid) &lt;
      (SELECT COALESCE(last_message_at,created_at),conversation_uuid FROM chat_conversations WHERE conversation_uuid=#{cursor})</if>
      ORDER BY COALESCE(last_message_at,created_at) DESC,conversation_uuid DESC LIMIT #{size}</script>
      """)
    List<ChatConversationDO> page(@Param("userId") long userId,@Param("customer") boolean customer,
    @Param("windows") List<Long> windows,@Param("cursor") String cursor,@Param("size") int size);
    /**
     * 统计授权会话中阅读水位之后由他人发送的消息。只用最大seq相减会误计自己的消息，因此使用条件计数。
     */
    @Select("""
      <script>SELECT COALESCE(SUM((SELECT COUNT(*) FROM chat_messages m WHERE m.conversation_id=c.id AND m.sender_id != #{userId} AND m.revoked_at IS NULL
      AND m.seq &gt; COALESCE((SELECT last_read_seq FROM chat_read_state r WHERE r.conversation_id=c.id AND r.user_id=#{userId}),0))),0)
      FROM chat_conversations c WHERE <choose><when test="customer">c.customer_id=#{userId}</when><otherwise>c.window_id IN
      <foreach collection="windows" item="w" open="(" separator="," close=")">#{w}</foreach></otherwise></choose></script>
      """)
    long unreadTotal(@Param("userId") long userId,@Param("customer") boolean customer,@Param("windows") List<Long> windows);
    /** 仅撤回末条消息时替换列表摘要，不改变序号和排序时间。 */
    @Update("UPDATE chat_conversations SET last_content='该消息已撤回' WHERE id=#{id} AND last_seq=#{seq}")
    int revokePreview(@Param("id") long id,@Param("seq") long seq);
}
