-- 在暂停旧写入并备份后执行；历史订单只补截止时间，不直接取消。
ALTER TABLE orders ADD COLUMN expires_at DATETIME NULL COMMENT '创建时固定的支付截止时间';
UPDATE orders SET expires_at=DATE_ADD(created_at,INTERVAL 15 MINUTE) WHERE expires_at IS NULL;
CREATE TABLE IF NOT EXISTS order_timeout_outbox (
 event_id VARCHAR(80) NOT NULL PRIMARY KEY COMMENT '幂等事件编号',
 order_id BIGINT NOT NULL COMMENT '订单内部编号',
 expires_at BIGINT NOT NULL COMMENT '支付截止毫秒时间戳',
 attempts INT NOT NULL DEFAULT 0 COMMENT '发送失败次数',
 next_attempt_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下次发送时间',
 published_at DATETIME NULL COMMENT '发布确认时间',
 dead_letter_at DATETIME NULL COMMENT '进入死信待人工处理时间',
 last_error VARCHAR(128) NULL COMMENT '脱敏错误类型',
 KEY idx_timeout_pending(published_at,next_attempt_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单超时消息本地事件表';
-- 为历史待支付订单补可靠事件；需先对账，激活后会处理已过期订单。
INSERT INTO order_timeout_outbox(event_id,order_id,expires_at)
 SELECT CONCAT('order-timeout-v1:',order_id),order_id,
 TIMESTAMPDIFF(SECOND,'1970-01-01 00:00:00',expires_at)*1000-28800000
 FROM orders WHERE status='待支付'
 ON DUPLICATE KEY UPDATE event_id=event_id;
