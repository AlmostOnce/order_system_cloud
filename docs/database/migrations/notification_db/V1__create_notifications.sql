CREATE DATABASE IF NOT EXISTS notification_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE notification_db;

-- 消息先持久化，再尝试实时推送；同一业务事件重投不会产生重复通知。
CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '内部顺序主键',
    notification_id CHAR(36) NOT NULL COMMENT '公开通知标识',
    recipient_id BIGINT NOT NULL COMMENT '接收用户内部编号',
    event_id VARCHAR(128) NOT NULL COMMENT '来源业务事件标识',
    notification_type VARCHAR(32) NOT NULL COMMENT '通知类型',
    title VARCHAR(120) NOT NULL COMMENT '通知标题',
    content VARCHAR(500) NOT NULL COMMENT '通知内容',
    order_id BIGINT DEFAULT NULL COMMENT '关联订单编号',
    read_at DATETIME(3) DEFAULT NULL COMMENT '阅读时间，空表示未读',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_public_id (notification_id),
    UNIQUE KEY uk_notification_recipient_event (recipient_id, event_id),
    KEY idx_notification_recipient_id (recipient_id, id),
    KEY idx_notification_recipient_unread (recipient_id, read_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户站内通知';
