-- 单次执行；新增关联号供一笔订单多次按菜品回补，旧流水不变。
USE stock_db;
ALTER TABLE stock_flow
 ADD COLUMN original_biz_ref VARCHAR(64) NULL COMMENT '部分退款关联的原订单号',
 ADD INDEX idx_stock_refund_origin(stock_id,original_biz_ref,biz_type);
