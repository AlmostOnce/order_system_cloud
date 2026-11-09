package com.hbue.ordering.notification.vo;
import java.time.LocalDateTime;
import com.hbue.ordering.notification.model.ChatMessageDO;
/**
 * 对外文字消息快照。seq只表示会话内顺序，senderUuid为公开身份；不暴露消息或用户内部主键。
 * @param messageUuid 消息公开唯一标识，用于合并去重
 * @param seq 会话内单调递增序号，用于历史和增量分页
 * @param senderUuid 实际发送人的公开用户UUID
 * @param senderName 发送时的昵称快照，店主多人处理时保留实际发送人
 * @param senderSide CUSTOMER或MERCHANT，表示发送时所属一侧
 * @param clientMessageId 客户端生成的幂等键，重试原消息时必须保持不变
 * @param content 已校验的纯文字正文，不作为HTML渲染
 * @param revokedAt 撤回时间，非空时仅返回撤回提示，屏蔽原文
 * @param createdAt 服务端UTC+8入库时间
 */
public record ChatMessageVO(String messageUuid, long seq, String senderUuid, String senderName,
String senderSide, String clientMessageId, String content, LocalDateTime createdAt, LocalDateTime revokedAt) {
    /**
     * 从持久化消息生成公开视图，只投影公开身份和显示所需内容，不返回内部主键。
     */
    public static ChatMessageVO from(ChatMessageDO m) {
        return new ChatMessageVO(m.getMessageUuid(),m.getSeq(),m.getSenderUuid(),
        m.getSenderName(),m.getSenderSide(),m.getClientMessageId(),m.getRevokedAt()==null?m.getContent():"该消息已撤回",m.getCreatedAt(),m.getRevokedAt());
    }
}
