-- 目标notification_db；为站内通知增加业务引用，支持从消息中心跳转到反馈详情。
-- 订单通知继续使用既有 order_id；工单通知写入 biz_type='SUPPORT' 与 biz_id=公开工单标识。
-- 通过 information_schema 判断列是否存在，脚本可重复执行。
USE notification_db;

SET
@ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='notifications' AND column_name='biz_type')=0,
 'ALTER TABLE notifications ADD COLUMN biz_type VARCHAR(16) DEFAULT NULL COMMENT ''业务类型，SUPPORT 表示反馈工单'' AFTER order_id', 'SELECT 1');
PREPARE notification_biz_type_migration FROM @ddl;
EXECUTE notification_biz_type_migration;
DEALLOCATE PREPARE notification_biz_type_migration;

SET
@ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='notifications' AND column_name='biz_id')=0,
 'ALTER TABLE notifications ADD COLUMN biz_id VARCHAR(64) DEFAULT NULL COMMENT ''业务公开标识，配合 biz_type 跳转详情'' AFTER biz_type', 'SELECT 1');
PREPARE notification_biz_id_migration FROM @ddl;
EXECUTE notification_biz_id_migration;
DEALLOCATE PREPARE notification_biz_id_migration;
