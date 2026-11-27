-- 修复反馈工单幂等键字段的中文注释编码。
-- 早期迁移执行时客户端按 latin1 解释中文，导致注释被二次编码；本脚本只重写注释，
-- 不改变列类型、可空性、默认值和数据。
SET NAMES utf8mb4;

USE support_db;

ALTER TABLE support_ticket
    MODIFY COLUMN request_key VARCHAR(64) NOT NULL COMMENT '创建幂等键';

ALTER TABLE support_ticket_message
    MODIFY COLUMN request_key VARCHAR(64) NULL COMMENT '补充说明幂等键；系统消息为空';
