-- 修复订单支付时间字段的中文注释编码。
-- 早期迁移执行时客户端按 latin1 解释中文，导致注释被二次编码；本脚本只重写注释，
-- 不改变列类型、可空性、默认值和数据。SET NAMES 让脚本无论客户端参数如何都按 utf8mb4 解释中文。
SET NAMES utf8mb4;

USE order_db;

ALTER TABLE orders
    MODIFY COLUMN paid_at DATETIME NULL COMMENT '支付首次成功时间，来源于支付服务 success_time，UTC+8';
