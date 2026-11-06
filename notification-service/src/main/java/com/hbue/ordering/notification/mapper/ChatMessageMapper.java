package com.hbue.ordering.notification.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hbue.ordering.notification.model.ChatMessageDO;
import org.apache.ibatis.annotations.*;
import java.util.List;
/**
 * 会话文字消息与阅读水位持久化。正文不拼接进SQL，使用绑定参数；历史与补拉按服务端seq分页。
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessageDO> {
    /**
     * 按会话、实际发送人和客户端消息UUID查已提交结果。相同键不同正文由Service报冲突。
     */
    @Select("SELECT * FROM chat_messages WHERE conversation_id=#{cid} AND sender_id=#{uid} AND client_message_id=#{key}")
    ChatMessageDO duplicate(@Param("cid") long cid,@Param("uid") long uid,@Param("key") String key);
    /**
     * 在Service已授权的会话内部按seq查询消息。before倒序取历史，after升序补增量；额外取一条判断是否还有下一页。
     */
    @Select("""
      <script>SELECT * FROM chat_messages WHERE conversation_id=#{cid}
      <if test="before != null">AND seq &lt; #{before}</if>
      <if test="after != null">AND seq &gt; #{after}</if>
      ORDER BY seq <choose><when test="after != null">ASC</when><otherwise>DESC</otherwise></choose> LIMIT #{size}</script>
      """)
    List<ChatMessageDO> page(@Param("cid") long cid,@Param("before") Long before,@Param("after") Long after,@Param("size") int size);
    /**
     * 统计指定会话中由他人发送、且序号大于本人阅读水位的消息；会话访问权限由Service在调用前核验。
     */
    @Select("SELECT COUNT(*) FROM chat_messages WHERE conversation_id=#{cid} AND sender_id != #{uid} AND seq > COALESCE((SELECT last_read_seq FROM chat_read_state WHERE conversation_id=#{cid} AND user_id=#{uid}),0)")
    long unread(@Param("cid") long cid,@Param("uid") long uid);
    /**
     * 写入已由Service验证的阅读水位。GREATEST防止旧请求回退水位，Mapper不自行认定页面已经展示消息。
     */
    @Insert("""
      INSERT INTO chat_read_state(conversation_id,user_id,last_read_seq) VALUES(#{cid},#{uid},#{seq})
      ON DUPLICATE KEY UPDATE last_read_seq=GREATEST(last_read_seq,#{seq}),updated_at=CURRENT_TIMESTAMP(3)
      """)
    int read(@Param("cid") long cid,@Param("uid") long uid,@Param("seq") long seq);
}
