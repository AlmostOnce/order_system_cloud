USE user_db;
-- 密码恢复与旧会话撤销在同一用户行事务中提交，已有账号/令牌按版本0兼容。
ALTER TABLE users ADD COLUMN credential_version BIGINT NOT NULL DEFAULT 0 COMMENT '登录凭据版本，找回密码后递增并撤销旧会话';
