-- 创建统计服务的销量事实和汇总读模型；订单支付事实仍归 order_db / payment_db 所有。
-- 脚本使用 IF NOT EXISTS、INSERT IGNORE 与按事实重算汇总，可在开发迁移中断后重跑。
-- 显式固定客户端字符集，避免中文订单状态条件被 MySQL 按其他编码解释而漏掉历史记录。
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS statistics_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS statistics_db.statistics_sales_events (
    event_id VARCHAR(80) NOT NULL COMMENT '稳定销量事件键：order-paid-sales:{orderId}',
    order_id BIGINT UNSIGNED NOT NULL COMMENT '来源订单主键，只用于幂等去重，不作为用户端标识',
    window_id BIGINT UNSIGNED NOT NULL COMMENT '下单时所属窗口主键快照',
    paid_at DATETIME NOT NULL COMMENT '订单首次支付成功时间，按 UTC+8 归属月份',
    event_version SMALLINT UNSIGNED NOT NULL COMMENT '事件格式版本，供历史兼容与排查',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '统计服务首次持久化事件时间',
    PRIMARY KEY (event_id),
    UNIQUE KEY uk_statistics_sales_order (order_id),
    KEY idx_statistics_sales_paid_at (paid_at, window_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='统计服务消费的已支付订单事实，用于幂等和读模型重建';

CREATE TABLE IF NOT EXISTS statistics_db.statistics_sales_event_items (
    event_id VARCHAR(80) NOT NULL COMMENT '所属已支付事件键',
    item_id BIGINT UNSIGNED NOT NULL COMMENT '订单明细主键快照，用于识别重复明细及选择最新菜名',
    product_id BIGINT UNSIGNED NOT NULL COMMENT '下单时菜品主键快照',
    product_name VARCHAR(255) NOT NULL COMMENT '下单时菜品名称快照',
    quantity INT UNSIGNED NOT NULL COMMENT '原始购买份数，退款不会减少销量',
    PRIMARY KEY (event_id, item_id),
    KEY idx_statistics_sales_item_product (product_id, item_id),
    CONSTRAINT fk_statistics_sales_item_event
        FOREIGN KEY (event_id) REFERENCES statistics_db.statistics_sales_events (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='已支付订单中的菜品快照明细';

CREATE TABLE IF NOT EXISTS statistics_db.statistics_sales_window_totals (
    period_type VARCHAR(8) NOT NULL COMMENT '汇总周期：ALL累计、MONTH自然月',
    period_key CHAR(7) NOT NULL COMMENT 'ALL累计固定为ALLTIME，自然月格式为YYYY-MM',
    window_id BIGINT UNSIGNED NOT NULL COMMENT '窗口主键',
    paid_quantity BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已支付订单原始购买总份数，不扣退款',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '汇总最后变更时间',
    PRIMARY KEY (period_type, period_key, window_id),
    KEY idx_statistics_window_quantity (period_type, period_key, paid_quantity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='窗口累计及自然月已支付销量汇总';

CREATE TABLE IF NOT EXISTS statistics_db.statistics_sales_product_totals (
    period_type VARCHAR(8) NOT NULL COMMENT '汇总周期：ALL累计、MONTH自然月',
    period_key CHAR(7) NOT NULL COMMENT 'ALL累计固定为ALLTIME，自然月格式为YYYY-MM',
    window_id BIGINT UNSIGNED NOT NULL COMMENT '下单时所属窗口主键',
    product_id BIGINT UNSIGNED NOT NULL COMMENT '下单时菜品主键',
    product_name VARCHAR(255) NOT NULL COMMENT '最近一次订单中的菜品名称快照',
    latest_item_id BIGINT UNSIGNED NOT NULL COMMENT '最近一次名称快照对应的订单明细主键',
    paid_quantity BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已支付订单原始购买总份数，不扣退款',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '汇总最后变更时间',
    PRIMARY KEY (period_type, period_key, window_id, product_id),
    KEY idx_statistics_product_rank (period_type, period_key, paid_quantity, product_id, window_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='菜品累计及自然月已支付销量汇总';

CREATE TABLE IF NOT EXISTS statistics_db.statistics_sales_projection_meta (
    projection_id TINYINT UNSIGNED NOT NULL COMMENT '固定单行主键，当前值为1',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'MySQL销量投影每次成功新增订单后递增的版本号',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '投影版本最后变更时间',
    PRIMARY KEY (projection_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='统计投影版本元数据';

INSERT IGNORE INTO statistics_db.statistics_sales_projection_meta (projection_id, version) VALUES (1, 0);

-- 初始化历史事件时只关联成功支付且仍属于已支付履约状态的订单；不以订单更新时间推测月份。
INSERT IGNORE INTO statistics_db.statistics_sales_events
    (event_id, order_id, window_id, paid_at, event_version)
SELECT CONCAT('order-paid-sales:', o.order_id), o.order_id, o.window_id, o.paid_at, 2
FROM order_db.orders AS o
WHERE o.paid_at IS NOT NULL
  AND o.status IN ('已支付', '制作中', '待取货', '已完成', '已退款')
  AND EXISTS (
      SELECT 1 FROM payment_db.payments AS p
      WHERE p.order_id = o.order_id
        AND p.status = 'SUCCESS'
        AND p.success_time = o.paid_at
  );

INSERT IGNORE INTO statistics_db.statistics_sales_event_items
    (event_id, item_id, product_id, product_name, quantity)
SELECT e.event_id, i.item_id, i.product_id, i.product_name_snapshot, i.quantity
FROM statistics_db.statistics_sales_events AS e
JOIN order_db.order_items AS i ON i.order_id = e.order_id;

-- 每次执行都从已持久化的事实明细重算汇总，避免迁移重跑时将历史份数再次累加。
INSERT INTO statistics_db.statistics_sales_window_totals
    (period_type, period_key, window_id, paid_quantity)
SELECT 'ALL', 'ALLTIME', e.window_id, SUM(i.quantity)
FROM statistics_db.statistics_sales_events AS e
JOIN statistics_db.statistics_sales_event_items AS i ON i.event_id = e.event_id
GROUP BY e.window_id
ON DUPLICATE KEY UPDATE paid_quantity = VALUES(paid_quantity);

INSERT INTO statistics_db.statistics_sales_window_totals
    (period_type, period_key, window_id, paid_quantity)
SELECT 'MONTH', DATE_FORMAT(e.paid_at, '%Y-%m'), e.window_id, SUM(i.quantity)
FROM statistics_db.statistics_sales_events AS e
JOIN statistics_db.statistics_sales_event_items AS i ON i.event_id = e.event_id
GROUP BY DATE_FORMAT(e.paid_at, '%Y-%m'), e.window_id
ON DUPLICATE KEY UPDATE paid_quantity = VALUES(paid_quantity);

INSERT INTO statistics_db.statistics_sales_product_totals
    (period_type, period_key, window_id, product_id, product_name, latest_item_id, paid_quantity)
SELECT 'ALL', 'ALLTIME', grouped.window_id, grouped.product_id, latest.product_name,
       latest.item_id, grouped.paid_quantity
FROM (
    SELECT e.window_id, i.product_id, SUM(i.quantity) AS paid_quantity, MAX(i.item_id) AS latest_item_id
    FROM statistics_db.statistics_sales_events AS e
    JOIN statistics_db.statistics_sales_event_items AS i ON i.event_id = e.event_id
    GROUP BY e.window_id, i.product_id
) AS grouped
JOIN statistics_db.statistics_sales_event_items AS latest
  ON latest.product_id = grouped.product_id AND latest.item_id = grouped.latest_item_id
JOIN statistics_db.statistics_sales_events AS latest_event
  ON latest_event.event_id = latest.event_id AND latest_event.window_id = grouped.window_id
ON DUPLICATE KEY UPDATE
    product_name = VALUES(product_name), latest_item_id = VALUES(latest_item_id), paid_quantity = VALUES(paid_quantity);

INSERT INTO statistics_db.statistics_sales_product_totals
    (period_type, period_key, window_id, product_id, product_name, latest_item_id, paid_quantity)
SELECT 'MONTH', grouped.period_key, grouped.window_id, grouped.product_id, latest.product_name,
       latest.item_id, grouped.paid_quantity
FROM (
    SELECT DATE_FORMAT(e.paid_at, '%Y-%m') AS period_key, e.window_id, i.product_id,
           SUM(i.quantity) AS paid_quantity, MAX(i.item_id) AS latest_item_id
    FROM statistics_db.statistics_sales_events AS e
    JOIN statistics_db.statistics_sales_event_items AS i ON i.event_id = e.event_id
    GROUP BY DATE_FORMAT(e.paid_at, '%Y-%m'), e.window_id, i.product_id
) AS grouped
JOIN statistics_db.statistics_sales_event_items AS latest
  ON latest.product_id = grouped.product_id AND latest.item_id = grouped.latest_item_id
JOIN statistics_db.statistics_sales_events AS latest_event
  ON latest_event.event_id = latest.event_id
 AND latest_event.window_id = grouped.window_id
 AND DATE_FORMAT(latest_event.paid_at, '%Y-%m') = grouped.period_key
ON DUPLICATE KEY UPDATE
    product_name = VALUES(product_name), latest_item_id = VALUES(latest_item_id), paid_quantity = VALUES(paid_quantity);

-- 以事实表行数初始化缓存版本；重跑脚本不会伪造新投影变更，也不会再次触发无意义的全量重建。
UPDATE statistics_db.statistics_sales_projection_meta
SET version = GREATEST(version, (SELECT COUNT(*) FROM statistics_db.statistics_sales_events))
WHERE projection_id = 1;

-- 统计库账号只需访问 statistics_db；脚本可重跑而不覆盖账号口令。
CREATE USER IF NOT EXISTS 'statistics_service'@'%' IDENTIFIED BY 'statistics_password_dev_2026';
GRANT SELECT, INSERT, UPDATE, DELETE ON statistics_db.* TO 'statistics_service'@'%';
FLUSH PRIVILEGES;
