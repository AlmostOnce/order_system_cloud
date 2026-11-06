USE notification_db;
-- 仅新增聊天表；执行前备份。回退应关闭聊天开关，保留消息数据，不自动DROP表。
CREATE TABLE IF NOT EXISTS chat_conversations (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '会话内部主键',
 conversation_uuid CHAR(36) NOT NULL COMMENT '公开会话UUID',
 order_id BIGINT NOT NULL COMMENT '关联订单编号',
 order_no VARCHAR(64) NOT NULL COMMENT '订单号快照',
 window_id BIGINT NOT NULL COMMENT '窗口内部编号',
 window_name VARCHAR(120) NOT NULL COMMENT '店铺名称快照',
 customer_id BIGINT NOT NULL COMMENT '下单顾客内部编号',
 last_seq BIGINT NOT NULL DEFAULT 0 COMMENT '会话内已提交消息最大序号',
 last_content VARCHAR(500) NOT NULL DEFAULT '' COMMENT '最后消息摘要',
 last_message_at DATETIME(3) DEFAULT NULL COMMENT '最近消息时间',
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
 PRIMARY KEY(id), UNIQUE KEY uk_chat_order(order_id), UNIQUE KEY uk_chat_uuid(conversation_uuid),
 KEY idx_chat_customer(customer_id,id), KEY idx_chat_window(window_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='订单商家顾客会话';
CREATE TABLE IF NOT EXISTS chat_messages (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '消息内部主键',
 message_uuid CHAR(36) NOT NULL COMMENT '公开消息UUID',
 conversation_id BIGINT NOT NULL COMMENT '会话内部主键',
 seq BIGINT NOT NULL COMMENT '会话内递增序号',
 sender_id BIGINT NOT NULL COMMENT '实际发送人内部编号',
 sender_uuid CHAR(36) NOT NULL COMMENT '实际发送人公开UUID',
 sender_name VARCHAR(100) NOT NULL COMMENT '发送人昵称快照',
 sender_side VARCHAR(16) NOT NULL COMMENT 'CUSTOMER或MERCHANT',
 client_message_id CHAR(36) NOT NULL COMMENT '客户端幂等消息UUID',
 content VARCHAR(500) NOT NULL COMMENT '纯文本正文',
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '服务端消息时间',
 PRIMARY KEY(id), UNIQUE KEY uk_chat_message_uuid(message_uuid),
 UNIQUE KEY uk_chat_sequence(conversation_id,seq),
 UNIQUE KEY uk_chat_idempotency(conversation_id,sender_id,client_message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='订单会话文字消息';
CREATE TABLE IF NOT EXISTS chat_read_state (
 conversation_id BIGINT NOT NULL COMMENT '会话内部主键',
 user_id BIGINT NOT NULL COMMENT '实际阅读人内部编号',
 last_read_seq BIGINT NOT NULL DEFAULT 0 COMMENT '最后已读序号，仅递增',
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '阅读更新时间',
 PRIMARY KEY(conversation_id,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话参与人的已读水位';
CREATE TABLE IF NOT EXISTS chat_event_outbox (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '投递记录内部编号',
 event_uuid CHAR(36) NOT NULL COMMENT '投递事件UUID',
 conversation_uuid CHAR(36) NOT NULL COMMENT '需补查的公开会话UUID',
 attempts BIGINT NOT NULL DEFAULT 0 COMMENT '已尝试次数',
 next_attempt_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '下次投递时间',
 delivered_at DATETIME(3) DEFAULT NULL COMMENT '发布成功时间，不代表客户端已读',
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '事件创建时间',
 PRIMARY KEY(id), UNIQUE KEY uk_chat_event(event_uuid), KEY idx_chat_pending(delivered_at,next_attempt_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天事务事件投递记录';
