package com.hbue.ordering.notification.model;

import java.time.LocalDateTime;

/**
 * 系统通知会话实体，对应 system_conversations 表。
 *
 * <p>一行代表一个用户的一类系统会话（当前为订单中心）。与通知表不同，会话是独立实体：
 * 用户首次打开消息页时创建，之后长期常驻，预期与时间随通知写入推进，
 * 因此不能再通过“有没有通知”反推会话是否存在。</p>
 *
 * @author order-system
 * @date 2026-11-26
 */
public class SystemConversationDO {

    /**
     * 内部主键。
     */
    private Long conversationId;

    /**
     * 接收通知的用户内部编号。
     */
    private Long customerId;

    /**
     * 系统会话类型，例如 ORDER 表示订单中心。
     */
    private String conversationType;

    /**
     * 会话名称，用于消息列表展示。
     */
    private String title;

    /**
     * 最新一条通知摘要。
     */
    private String preview;

    /**
     * 最新通知时间；为 null 表示会话已常驻但还没有通知。
     */
    private LocalDateTime latestAt;

    /**
     * 会话创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 最后更新时间。
     */
    private LocalDateTime updatedAt;

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getConversationType() {
        return conversationType;
    }

    public void setConversationType(String conversationType) {
        this.conversationType = conversationType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPreview() {
        return preview;
    }

    public void setPreview(String preview) {
        this.preview = preview;
    }

    public LocalDateTime getLatestAt() {
        return latestAt;
    }

    public void setLatestAt(LocalDateTime latestAt) {
        this.latestAt = latestAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
