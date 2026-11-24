-- 学生账号注销：72 小时冷静期 + 到期匿名化。
-- 执行前备份 users 与 balance_records；本脚本只增加字段、索引和放宽流水类型约束，
-- 不修改历史业务数据。MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，重复执行会报错，
-- 重新执行前先查询 information_schema.columns / statistics 确认对象是否已存在。
-- 执行时必须带 --default-character-set=utf8mb4，否则中文注释会被客户端按 latin1 二次编码成乱码。
SET NAMES utf8mb4;

USE user_db;

-- 注销申请时间：为空表示当前账号没有待生效的注销申请。
ALTER TABLE users
    ADD COLUMN deletion_requested_at DATETIME NULL COMMENT '注销申请提交时间';

-- 注销生效时间：申请时间 + 配置的冷静期（默认 72 小时）。
-- 到期任务按该列扫描；待生效期间登录不签发正常令牌。
ALTER TABLE users
    ADD COLUMN deletion_effective_at DATETIME NULL COMMENT '注销生效时间，申请后默认72小时';

-- 24 小时提醒邮件发送时间：条件更新该列抢占提醒权，保证同一申请只提醒一次。
ALTER TABLE users
    ADD COLUMN deletion_reminded_at DATETIME NULL COMMENT '注销生效前24小时提醒邮件发送时间';

-- 到期任务只扫描有待生效注销且尚未匿名化的账号，单列索引避免全表扫描。
ALTER TABLE users
    ADD KEY idx_users_deletion_effective (deletion_effective_at);

-- 原约束只允许 RECHARGE / PAY / REFUND，注销时余额放弃需要新增 WRITE_OFF。
-- 先删除旧约束再新增，保证余额流水类型与实际记账动作一致。
ALTER TABLE balance_records
    DROP CHECK chk_balance_records_change_type;

ALTER TABLE balance_records
    ADD CONSTRAINT chk_balance_records_change_type
        CHECK (change_type IN ('RECHARGE', 'PAY', 'REFUND', 'WRITE_OFF'));

-- 核验语句（人工执行，不修改数据）：
-- SHOW COLUMNS FROM users LIKE 'deletion%';
-- SHOW INDEX FROM users WHERE Key_name = 'idx_users_deletion_effective';
-- SELECT CONSTRAINT_NAME, CHECK_CLAUSE FROM information_schema.CHECK_CONSTRAINTS
--  WHERE CONSTRAINT_SCHEMA = 'user_db' AND CONSTRAINT_NAME = 'chk_balance_records_change_type';
