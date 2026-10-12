-- 支付服务：创建 payment_db 数据库与支付单表。
-- 执行方式（root 权限，幂等可重复执行）：
--   wsl -- bash -c "docker exec -i order-system-cloud-mysql-1 sh -c 'mysql -uroot -p\"$MYSQL_ROOT_PASSWORD\" --default-character-set=utf8mb4' < /mnt/e/order_system_cloud/docs/database/migrations/payment_db/V1__create_payments_table.sql"
-- 执行后需确认 payment_service 账号已授权（本文件末尾包含账号授权语句）。

CREATE DATABASE IF NOT EXISTS payment_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE payment_db;

CREATE TABLE IF NOT EXISTS payments (
    payment_id       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '支付单主键',
    payment_no       VARCHAR(32)  NOT NULL COMMENT '业务支付单号',
    order_id         BIGINT       NOT NULL COMMENT '订单 ID（跨库逻辑关联，不设外键）',
    order_no         VARCHAR(32)  NOT NULL COMMENT '订单号快照，便于排查',
    user_id          BIGINT       NOT NULL COMMENT '付款用户快照，归属校验与对账用',
    channel_code     VARCHAR(32)  NOT NULL DEFAULT 'MOCK' COMMENT '支付渠道标识',
    amount           DECIMAL(10,2) NOT NULL COMMENT '应付金额（创建时订单快照）',
    paid_amount      DECIMAL(10,2) NULL COMMENT '实收金额（回调成功时写入）',
    channel_trade_no VARCHAR(64)  NULL COMMENT '渠道交易号',
    status           VARCHAR(16)  NOT NULL DEFAULT 'CREATED' COMMENT '支付单状态：CREATED/SUCCESS/FAILED/CLOSED',
    fail_reason      VARCHAR(255) NULL COMMENT '失败原因或异常备注',
    notify_count     INT          NOT NULL DEFAULT 0 COMMENT '累计收到回调次数',
    last_notify_at   DATETIME     NULL COMMENT '最近一次回调时间',
    success_time     DATETIME     NULL COMMENT '支付成功时间',
    active_flag      TINYINT GENERATED ALWAYS AS (
                         CASE WHEN status = 'CREATED' THEN 1 ELSE NULL END
                     ) VIRTUAL COMMENT '进行中标记：同一订单最多只有一个 CREATED 支付单',
    created_at       DATETIME     NOT NULL COMMENT '创建时间',
    updated_at       DATETIME     NOT NULL COMMENT '更新时间',
    PRIMARY KEY (payment_id),
    UNIQUE KEY uk_payments_payment_no (payment_no),
    UNIQUE KEY uk_payments_order_active (order_id, active_flag),
    KEY idx_payments_user_created (user_id, created_at),
    KEY idx_payments_order (order_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 支付服务数据库账号（仅授予 payment_db 权限，幂等可重复执行）。
CREATE USER IF NOT EXISTS 'payment_service'@'%' IDENTIFIED BY 'payment_password_dev_2026';
GRANT ALL PRIVILEGES ON payment_db.* TO 'payment_service'@'%';
FLUSH PRIVILEGES;
