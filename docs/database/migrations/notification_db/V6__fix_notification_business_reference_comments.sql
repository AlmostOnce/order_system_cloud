-- 修复站内通知业务引用字段的中文注释编码。
-- 早期迁移执行时客户端按 latin1 解释中文，导致注释被二次编码；本脚本只重写注释，
-- 不改变列类型、可空性、默认值和数据。
SET NAMES utf8mb4;

USE notification_db;

ALTER TABLE notifications
    MODIFY COLUMN biz_type VARCHAR(16) NULL COMMENT '业务类型，SUPPORT 表示反馈工单',
    MODIFY COLUMN biz_id VARCHAR(64) NULL COMMENT '业务公开标识，配合 biz_type 跳转详情';
