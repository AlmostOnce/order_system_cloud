USE product_db;

-- 档口信息补全：对照旧单体项目 order-sys-back 的 windows 表（image_url、is_open）。
-- is_open 与 status 语义不同：status 是管理口径的启用/停用（停用后档口不可接单），
-- is_open 是营业口径的临时开关（备餐、休息等），两者相互独立。
--
-- 注意：本迁移只执行一次；MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，重复执行会报
-- Duplicate column name。重复执行前先查询 information_schema.columns 判断列是否存在。
ALTER TABLE dining_windows
    ADD COLUMN image_url VARCHAR(255) NULL AFTER location,
    ADD COLUMN is_open TINYINT NOT NULL DEFAULT 1 AFTER status;
