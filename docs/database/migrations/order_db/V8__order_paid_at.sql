-- 订单首次确认支付时保存支付服务的 success_time；后续状态更新及退款不会改写此字段。
-- 脚本对列检查后再执行 ALTER，可在迁移中断后安全重跑；回填只覆盖 NULL，不推测支付时间。
-- 明确客户端字符集，确保中文状态条件按订单库 utf8mb4 字符串进行比较。
SET NAMES utf8mb4;

SET @paid_at_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'order_db'
      AND TABLE_NAME = 'orders'
      AND COLUMN_NAME = 'paid_at'
);
SET @paid_at_ddl = IF(
    @paid_at_column_exists = 0,
    'ALTER TABLE order_db.orders ADD COLUMN paid_at DATETIME NULL COMMENT ''支付首次成功时间，来源于支付服务 success_time，UTC+8'' AFTER created_at',
    'SELECT 1'
);
PREPARE paid_at_statement FROM @paid_at_ddl;
EXECUTE paid_at_statement;
DEALLOCATE PREPARE paid_at_statement;

-- 历史仅采用真实成功支付记录；若同一订单存在多笔成功流水，取最早成功时间作为首次付款时刻。
UPDATE order_db.orders AS o
JOIN (
    SELECT order_id, MIN(success_time) AS paid_at
    FROM payment_db.payments
    WHERE status = 'SUCCESS'
      AND success_time IS NOT NULL
    GROUP BY order_id
) AS p ON p.order_id = o.order_id
SET o.paid_at = p.paid_at
WHERE o.paid_at IS NULL
  AND o.status IN ('已支付', '制作中', '待取货', '已完成', '已退款');
