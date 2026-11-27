package com.hbue.ordering.notification.model;

import java.time.LocalDateTime;

/**
 * 通知偏好表持久化对象。
 *
 * <p>每条记录表示一个用户对一个通知分类的显式设置；缺少记录表示使用默认开启，
 * 因此查询不到数据不是异常，而是用户从未修改过该分类。</p>
 *
 * <p>本对象只在通知服务内部使用，不直接返回给客户端；对外展示使用
 * {@code NotificationPreferenceVO}，避免暴露内部用户编号。</p>
 *
 * @author order-system
 * @date 2026-11-24
 */
public class NotificationPreferenceDO {

    /**
     * 用户内部编号，与通知接收人 recipient_id 使用同一账号范围。
     */
    private Long userId;

    /**
     * 通知分类编码，取值见 {@link NotificationCategory}。
     */
    private String category;

    /**
     * 是否开启提醒；true 开启，false 关闭。关闭只抑制提醒，不删除通知记录。
     */
    private Boolean enabled;

    /**
     * 最后修改时间，由数据库在插入和更新时维护。
     */
    private LocalDateTime updatedAt;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
