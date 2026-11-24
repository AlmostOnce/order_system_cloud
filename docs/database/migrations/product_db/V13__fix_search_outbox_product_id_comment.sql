-- 修复搜索事件菜品字段的中文注释编码。
-- 早期迁移执行时客户端按 latin1 解释中文，导致注释被二次编码；本脚本只重写注释，
-- 不改变列类型、可空性、默认值和数据。SET NAMES 让脚本无论客户端参数如何都按 utf8mb4 解释中文。
SET NAMES utf8mb4;

USE product_db;

ALTER TABLE product_search_outbox
    MODIFY COLUMN product_id BIGINT NULL COMMENT '菜品 ID；为空表示刷新整个窗口';
