-- 修复统计销量读模型的字段与表注释编码。
-- 早期迁移执行时客户端按 latin1 解释中文，导致注释被二次编码；本脚本只重写注释，
-- 不改变列类型、可空性、默认值、索引和数据。SET NAMES 让脚本按 utf8mb4 解释中文。
SET NAMES utf8mb4;

USE statistics_db;

ALTER TABLE statistics_sales_events
    MODIFY COLUMN event_id VARCHAR(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '稳定销量事件键：order-paid-sales:{orderId}',
    MODIFY COLUMN order_id BIGINT UNSIGNED NOT NULL COMMENT '来源订单主键，只用于幂等去重，不作为用户端标识',
    MODIFY COLUMN window_id BIGINT UNSIGNED NOT NULL COMMENT '下单时所属窗口主键快照',
    MODIFY COLUMN paid_at DATETIME NOT NULL COMMENT '订单首次支付成功时间，按 UTC+8 归属月份',
    MODIFY COLUMN event_version SMALLINT UNSIGNED NOT NULL COMMENT '事件格式版本，供历史兼容与排查',
    MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '统计服务首次持久化事件时间',
    COMMENT = '统计服务消费的已支付订单事实，用于幂等和读模型重建';

ALTER TABLE statistics_sales_event_items
    MODIFY COLUMN event_id VARCHAR(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属已支付事件键',
    MODIFY COLUMN item_id BIGINT UNSIGNED NOT NULL COMMENT '订单明细主键快照，用于识别重复明细及选择最新菜名',
    MODIFY COLUMN product_id BIGINT UNSIGNED NOT NULL COMMENT '下单时菜品主键快照',
    MODIFY COLUMN product_name VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '下单时菜品名称快照',
    MODIFY COLUMN quantity INT UNSIGNED NOT NULL COMMENT '原始购买份数，退款不会减少销量',
    COMMENT = '已支付订单中的菜品快照明细';

ALTER TABLE statistics_sales_window_totals
    MODIFY COLUMN period_type VARCHAR(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '汇总周期：ALL累计、MONTH自然月',
    MODIFY COLUMN period_key CHAR(7) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ALL累计固定为ALLTIME，自然月格式为YYYY-MM',
    MODIFY COLUMN window_id BIGINT UNSIGNED NOT NULL COMMENT '窗口主键',
    MODIFY COLUMN paid_quantity BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已支付订单原始购买总份数，不扣退款',
    MODIFY COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '汇总最后变更时间',
    COMMENT = '窗口累计及自然月已支付销量汇总';

ALTER TABLE statistics_sales_product_totals
    MODIFY COLUMN period_type VARCHAR(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '汇总周期：ALL累计、MONTH自然月',
    MODIFY COLUMN period_key CHAR(7) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ALL累计固定为ALLTIME，自然月格式为YYYY-MM',
    MODIFY COLUMN window_id BIGINT UNSIGNED NOT NULL COMMENT '下单时所属窗口主键',
    MODIFY COLUMN product_id BIGINT UNSIGNED NOT NULL COMMENT '下单时菜品主键',
    MODIFY COLUMN product_name VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '最近一次订单中的菜品名称快照',
    MODIFY COLUMN latest_item_id BIGINT UNSIGNED NOT NULL COMMENT '最近一次名称快照对应的订单明细主键',
    MODIFY COLUMN paid_quantity BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已支付订单原始购买总份数，不扣退款',
    MODIFY COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '汇总最后变更时间',
    COMMENT = '菜品累计及自然月已支付销量汇总';

ALTER TABLE statistics_sales_projection_meta
    MODIFY COLUMN projection_id TINYINT UNSIGNED NOT NULL COMMENT '固定单行主键，当前值为1',
    MODIFY COLUMN version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'MySQL销量投影每次成功新增订单后递增的版本号',
    MODIFY COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '投影版本最后变更时间',
    COMMENT = '统计投影版本元数据';
