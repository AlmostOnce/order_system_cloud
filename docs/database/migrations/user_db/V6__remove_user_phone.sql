-- 用户账号仅使用用户号或邮箱；执行前备份 users 表。
-- 保留 V1 等已执行迁移的历史，新库依次执行到 V6 后同样不再包含 phone。
ALTER TABLE users
    DROP INDEX uk_users_phone,
    DROP COLUMN phone;
