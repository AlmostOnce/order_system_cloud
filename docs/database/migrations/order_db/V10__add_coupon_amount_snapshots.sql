-- 优惠券金额快照迁移。需先检查目标库当前列并备份；只对缺失列执行一次性 ALTER。
-- 本地 V9 商品规格迁移新增的列与本文件无关；保留 V10 以避免迁移文件名冲突。

USE order_db;

ALTER TABLE orders
    ADD COLUMN user_coupon_id BIGINT UNSIGNED NULL COMMENT '下单锁定的用户券 ID，不建立跨库外键',
    ADD COLUMN coupon_title_snapshot VARCHAR(120) NULL COMMENT '下单时优惠券标题快照',
    ADD COLUMN discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '订单优惠总额快照',
    ADD COLUMN payable_amount DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '订单应付金额快照';

UPDATE orders
SET payable_amount = total_amount
WHERE payable_amount = 0
  AND total_amount > 0;

ALTER TABLE order_items
    ADD COLUMN line_discount_amount DECIMAL(12, 2) NOT NULL DEFAULT 0 COMMENT '本明细分摊的优惠金额快照',
    ADD COLUMN line_payable_amount DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '本明细优惠后的实付金额快照';

UPDATE order_items
SET line_payable_amount = line_amount
WHERE line_payable_amount = 0
  AND line_amount > 0;

CREATE TABLE IF NOT EXISTS order_coupon_consume_outbox
(
    event_id
    VARCHAR
(
    64
) NOT NULL COMMENT '核销消息事件唯一 ID',
    order_no VARCHAR
(
    64
) NOT NULL COMMENT '已确认支付订单号',
    user_coupon_id BIGINT UNSIGNED NOT NULL COMMENT '订单已锁定的用户券 ID',
    payload MEDIUMTEXT NOT NULL COMMENT 'RocketMQ 消息 JSON 快照',
    status VARCHAR
(
    16
) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/PUBLISHED/CONFIRMED',
    attempts INT NOT NULL DEFAULT 0 COMMENT '发送尝试次数',
    next_attempt_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下一次发送时间',
    last_error VARCHAR
(
    255
) NULL COMMENT '最近发送错误摘要',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    published_at DATETIME NULL COMMENT '首次进入 Broker 的时间',
    confirmed_at DATETIME NULL COMMENT '券服务提交核销后的消费确认时间',
    PRIMARY KEY
(
    event_id
),
    UNIQUE KEY uk_coupon_consume_order
(
    order_no
),
    KEY idx_coupon_consume_pending
(
    status,
    next_attempt_at,
    event_id
)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE =utf8mb4_unicode_ci COMMENT='支付成功后的优惠券核销 Outbox';
