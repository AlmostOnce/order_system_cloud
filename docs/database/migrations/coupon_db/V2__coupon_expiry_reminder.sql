-- 优惠券到期前 24 小时提醒所需的标记列与扫描索引。
-- 设计要点：
--   1. 用 expiry_reminded_at 而不是布尔位记录"已提醒"，同时保留提醒时间便于排查漏发；
--      NULL 表示尚未提醒，写入后永不回退，保证同一张券只提醒一次。
--   2. 扫描条件是"可用状态 + 未提醒 + 24 小时内到期"，索引按这三列建立，
--      避免全表扫描；user_coupon 增长后普通范围查询仍能走索引。
--   3. 只给 AVAILABLE 券发提醒：已锁定、已使用、已过期、已撤销的券提醒没有意义。
-- 全部语句按 information_schema 判断后再执行，重复执行不会报错。
USE coupon_db;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns
                     WHERE table_schema = DATABASE() AND table_name = 'user_coupon'
                       AND column_name = 'expiry_reminded_at'),
              'SELECT 1',
              'ALTER TABLE user_coupon ADD COLUMN expiry_reminded_at DATETIME(3) NULL COMMENT ''到期前24小时提醒发送时间；NULL表示未提醒''');
PREPARE coupon_ddl FROM @ddl;
EXECUTE coupon_ddl;
DEALLOCATE PREPARE coupon_ddl;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.statistics
                     WHERE table_schema = DATABASE() AND table_name = 'user_coupon'
                       AND index_name = 'idx_user_coupon_expiry_reminder'),
              'SELECT 1',
              'ALTER TABLE user_coupon ADD INDEX idx_user_coupon_expiry_reminder (status, expiry_reminded_at, valid_until)');
PREPARE coupon_ddl FROM @ddl;
EXECUTE coupon_ddl;
DEALLOCATE PREPARE coupon_ddl;
