-- 单次执行，先备份balance_records并核查字段未存在；新增可空列不改变历史流水。
USE user_db;
ALTER TABLE balance_records
 ADD COLUMN original_biz_ref VARCHAR(64) NULL COMMENT '退款关联的原支付单号；旧流水为空',
 ADD INDEX idx_balance_refund_origin(user_id,original_biz_ref,change_type);
