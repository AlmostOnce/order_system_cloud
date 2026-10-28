USE order_db;

-- 与订单状态更新加入同一本地事务，避免状态已提交而通知事件丢失。
CREATE TABLE IF NOT EXISTS order_notification_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '内部顺序主键',
    event_id VARCHAR(128) NOT NULL COMMENT '订单状态事件标识',
    order_id BIGINT NOT NULL COMMENT '订单内部编号',
    order_version INT NOT NULL COMMENT '状态更新后的订单版本',
    payload JSON NOT NULL COMMENT '事件内容',
    attempts INT NOT NULL DEFAULT 0 COMMENT '发送尝试次数',
    next_attempt_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '下次发送时间',
    published_at DATETIME(3) DEFAULT NULL COMMENT '消息中间件确认时间',
    last_error VARCHAR(200) DEFAULT NULL COMMENT '最近失败摘要',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_notification_event (event_id),
    UNIQUE KEY uk_order_notification_version (order_id, order_version),
    KEY idx_order_notification_pending (published_at, next_attempt_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单通知事件待发送表';
