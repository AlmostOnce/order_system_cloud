USE notification_db;
SET NAMES utf8mb4;

-- V9 建表时只写了 DEFAULT CHARSET=utf8mb4，MySQL 8 会默认落到 utf8mb4_0900_ai_ci，
-- 与 notification_db 以及 notifications/notification_preferences 的 utf8mb4_unicode_ci 不一致。
-- 中文排序、比较和后续跨表 JOIN 可能因此出现行为差异，这里统一回库默认排序规则。
-- 表刚创建且无业务数据，CONVERT 不涉及数据转换风险；重复执行结果一致。
ALTER TABLE system_conversations
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
