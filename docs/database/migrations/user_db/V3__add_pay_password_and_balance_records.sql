USE user_db;

-- 余额支付独立密码：与登录密码 password_hash 完全分离。
-- 账号被盗时，登录态被窃取不应直接导致余额被花掉，余额支付需要第二道凭据。
-- 该列不参与 JWT，只在余额相关接口的请求体里传入；未设置前禁止使用余额。
-- 允许为 NULL：存量账号与普通注册用户默认为“未设置支付密码”。
-- 校验失败的计数与锁定不入库，使用 Redis 带 TTL 的计数器实现（临时状态，无需持久化）。
--
-- 注意：本迁移只执行一次；MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，重复执行会报
-- Duplicate column name。重复执行前先查询 information_schema.columns 判断列是否存在。
ALTER TABLE users
    ADD COLUMN pay_password_hash VARCHAR(100) NULL AFTER password_hash;

-- 余额流水：与 users.balance 同库同事务写入，供对账与审计。
-- change_type：RECHARGE（入账）/ PAY（消费）/ REFUND（退回）。
-- biz_type + biz_ref 指向业务单据（如 PAYMENT + payment_no、ORDER + order_no）。
-- 唯一键 uk_balance_records_biz 让同一业务单据的同一动作只能记一次账，
-- 重复回调或重试直接冲突失败，保证记账幂等。
CREATE TABLE IF NOT EXISTS balance_records (
    record_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    change_type VARCHAR(20) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    balance_after DECIMAL(10, 2) NOT NULL,
    biz_type VARCHAR(20) NULL,
    biz_ref VARCHAR(64) NULL,
    remark VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (record_id),
    UNIQUE KEY uk_balance_records_biz (user_id, biz_type, biz_ref, change_type),
    KEY idx_balance_records_user_created (user_id, created_at),
    CONSTRAINT chk_balance_records_change_type
        CHECK (change_type IN ('RECHARGE', 'PAY', 'REFUND')),
    CONSTRAINT chk_balance_records_balance_after
        CHECK (balance_after >= 0)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
