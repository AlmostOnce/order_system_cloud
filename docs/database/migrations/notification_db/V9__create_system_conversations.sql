USE notification_db;
-- 显式声明连接字符集，保证下方中文注释按 UTF-8 解析，不受客户端默认字符集影响。
SET NAMES utf8mb4;

-- 系统通知会话实体：消息页的「订单中心」等系统会话不再由查询临时拼装，而是持久化为独立实体。
-- 与微信服务号的会话模型一致：会话存在即常驻，已读不会消失；只有从未创建过的会话才不出现。
-- 预览与时间由通知写入时推进；latest_at 允许为空，表示用户已拥有会话但尚无通知。
CREATE TABLE IF NOT EXISTS system_conversations (
    conversation_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '内部主键',
    customer_id BIGINT NOT NULL COMMENT '接收通知的用户内部编号',
    conversation_type VARCHAR(32) NOT NULL COMMENT '系统会话类型：ORDER 订单中心',
    title VARCHAR(64) NOT NULL COMMENT '会话名称，由写入方维护',
    preview VARCHAR(255) NOT NULL DEFAULT '' COMMENT '最新一条通知摘要',
    latest_at DATETIME(3) NULL COMMENT '最新通知时间，NULL 表示尚无通知',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '会话创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    PRIMARY KEY (conversation_id),
    UNIQUE KEY uk_system_conversation (customer_id, conversation_type),
    KEY idx_system_conversation_latest (customer_id, latest_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统通知会话实体';
