-- =============================================================================
-- 反馈工单幂等键列宽调整
--
-- 背景：接口契约要求幂等键为 8-64 位字母、数字、下划线或短横线，并不限定 UUID；
--       V1 使用 CHAR(36) 只能容纳标准 UUID，超过 36 位的合法键在 MySQL 严格模式下
--       会因超长写入失败。本脚本把两张表的 request_key 扩为 VARCHAR(64)。
--
-- 幂等性：ALTER TABLE ... MODIFY COLUMN 可重复执行，重复执行不会改变结果；
--         现有唯一键会自动跟随列类型，不需要重建。
-- 影响：仅放宽存储长度，不修改已有数据；VARCHAR(64) 在 utf8mb4 下索引长度
--       与 customer_id 组合后仍远低于 InnoDB 3072 字节上限。
-- =============================================================================

USE support_db;

ALTER TABLE support_ticket
    MODIFY COLUMN request_key VARCHAR(64) NOT NULL COMMENT '创建幂等键';

ALTER TABLE support_ticket_message
    MODIFY COLUMN request_key VARCHAR(64) NULL COMMENT '补充说明幂等键；系统消息为空';
