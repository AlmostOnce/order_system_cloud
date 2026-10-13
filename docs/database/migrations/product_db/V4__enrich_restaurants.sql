USE product_db;

-- 餐厅（食堂）信息补全。
-- 对照旧单体项目 order-sys-back 的 canteen 表（canteen_name / floor / phone），
-- 把新版 restaurants 缺的字段补齐，并增加门面图、公告与展示排序。
-- 不含营业时间：如需按餐段控制售卖，应由档口/菜品的售卖时段承担，不放在餐厅这一层。
--
-- 注意：本迁移只执行一次；MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，重复执行会报
-- Duplicate column name。重复执行前先查询 information_schema.columns 判断列是否存在。
ALTER TABLE restaurants
    ADD COLUMN location VARCHAR(64) NOT NULL DEFAULT '' AFTER restaurant_name,
    ADD COLUMN phone VARCHAR(20) NULL AFTER location,
    ADD COLUMN image_url VARCHAR(255) NULL AFTER phone,
    ADD COLUMN notice VARCHAR(255) NULL AFTER image_url,
    ADD COLUMN sort_order INT NOT NULL DEFAULT 0 AFTER notice;
