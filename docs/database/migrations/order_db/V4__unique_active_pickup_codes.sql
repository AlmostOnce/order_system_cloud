-- 执行前请先检查下列查询是否返回记录，随后部署支持碰撞重试的新签发代码。
-- 如有重码，需要对涉及的订单重新签发，不能任意选取其中一单核销。
USE order_db;
SELECT pickup_code, COUNT(*) AS duplicate_count
FROM orders
WHERE status IN ('已支付','制作中','待取货') AND pickup_code IS NOT NULL AND pickup_code <> ''
GROUP BY pickup_code HAVING COUNT(*) > 1;

-- 原子添加生成列与唯一索引：存在重码时整条 ALTER 失败，不自动更改已签发凭据。
-- 完成、取消等状态自动变为 NULL，MySQL 唯一索引允许多个 NULL，释放六位码空间。
ALTER TABLE orders
    ADD COLUMN active_pickup_code VARCHAR(32) GENERATED ALWAYS AS (
        CASE WHEN status IN ('已支付','制作中','待取货') AND pickup_code <> ''
        THEN pickup_code ELSE NULL END
    ) VIRTUAL COMMENT '活跃订单取餐码，自动随终态释放，仅用于唯一约束和核销定位',
    ADD UNIQUE KEY uk_order_active_pickup_code (active_pickup_code);
