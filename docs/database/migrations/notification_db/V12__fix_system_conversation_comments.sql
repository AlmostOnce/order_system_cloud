USE notification_db;
SET NAMES utf8mb4;

-- V9 首次执行时客户端连接字符集不是 utf8mb4，中文注释被按 latin1 解释后写入，
-- 形成“UTF-8 字节再编码”的乱码。V10 的 CONVERT 只改字符集，不会修复注释内容，
-- 因此这里用显式 UTF-8 连接逐列重写注释。MODIFY 不改变列类型、默认值、索引与自增属性，
-- 重复执行结果一致。
ALTER TABLE system_conversations COMMENT='系统通知会话实体';

ALTER TABLE system_conversations
    MODIFY COLUMN conversation_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '内部主键',
    MODIFY COLUMN customer_id BIGINT NOT NULL COMMENT '接收通知的用户内部编号',
    MODIFY COLUMN conversation_type VARCHAR(32) NOT NULL COMMENT '系统会话类型：ORDER 订单中心',
    MODIFY COLUMN title VARCHAR(64) NOT NULL COMMENT '会话名称，由写入方维护',
    MODIFY COLUMN preview VARCHAR(255) NOT NULL DEFAULT '' COMMENT '最新一条通知摘要',
    MODIFY COLUMN latest_at DATETIME(3) NULL COMMENT '最新通知时间，NULL 表示尚无通知',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '会话创建时间',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间';
