-- 商品演示数据中文乱码修复脚本。
--
-- 背景：本机 MySQL 命令行连接默认使用 latin1 时，UTF-8 文件里的中文会被当成
-- latin1 字符再次编码存储，形成「二次编码」乱码（例如「鸡肉饭」变成「é¸¡è‚‰é¥­」）。
-- 表与列的字符集本身仍是 utf8mb4，因此只需把被二次编码的内容还原。
--
-- 还原方式：CONVERT(col USING latin1) 取回原始字节，再按 utf8mb4 重新解释。
-- 安全判断：只有当该列能完整转换为 latin1 时才改写。正常中文无法用 latin1 表示，
-- 转换后会得到问号（HEX 中出现 3F），这类行会被跳过，因此脚本可以重复执行，
-- 不会把已经正常的中文覆盖成问号。
--
-- 执行前提：连接必须使用 utf8mb4，且脚本文件本身以 UTF-8 保存。
-- 执行方式：
--   docker exec -i order-system-cloud-mysql-1 mysql -uroot -p<密码> --default-character-set=utf8mb4 < docs/database/seeds/product_catalog_encoding_repair.sql

SET NAMES utf8mb4;
USE product_db;

-- 菜品名称。
UPDATE products
SET product_name = CONVERT(CAST(CONVERT(product_name USING latin1) AS BINARY) USING utf8mb4)
WHERE product_name IS NOT NULL
  AND HEX(CONVERT(product_name USING latin1)) NOT LIKE '%3F%'
  AND BINARY product_name <> BINARY CONVERT(CAST(CONVERT(product_name USING latin1) AS BINARY) USING utf8mb4);

-- 菜品描述。
UPDATE products
SET description = CONVERT(CAST(CONVERT(description USING latin1) AS BINARY) USING utf8mb4)
WHERE description IS NOT NULL
  AND HEX(CONVERT(description USING latin1)) NOT LIKE '%3F%'
  AND BINARY description <> BINARY CONVERT(CAST(CONVERT(description USING latin1) AS BINARY) USING utf8mb4);

-- 分类名称（保留分类主键，避免与菜品的外键关联被打断）。
UPDATE product_categories
SET category_name = CONVERT(CAST(CONVERT(category_name USING latin1) AS BINARY) USING utf8mb4)
WHERE category_name IS NOT NULL
  AND HEX(CONVERT(category_name USING latin1)) NOT LIKE '%3F%'
  AND BINARY category_name <> BINARY CONVERT(CAST(CONVERT(category_name USING latin1) AS BINARY) USING utf8mb4);

-- 档口名称。
UPDATE dining_windows
SET window_name = CONVERT(CAST(CONVERT(window_name USING latin1) AS BINARY) USING utf8mb4)
WHERE window_name IS NOT NULL
  AND HEX(CONVERT(window_name USING latin1)) NOT LIKE '%3F%'
  AND BINARY window_name <> BINARY CONVERT(CAST(CONVERT(window_name USING latin1) AS BINARY) USING utf8mb4);

-- 档口位置。
UPDATE dining_windows
SET location = CONVERT(CAST(CONVERT(location USING latin1) AS BINARY) USING utf8mb4)
WHERE location IS NOT NULL
  AND HEX(CONVERT(location USING latin1)) NOT LIKE '%3F%'
  AND BINARY location <> BINARY CONVERT(CAST(CONVERT(location USING latin1) AS BINARY) USING utf8mb4);

-- 餐厅名称。
UPDATE restaurants
SET restaurant_name = CONVERT(CAST(CONVERT(restaurant_name USING latin1) AS BINARY) USING utf8mb4)
WHERE restaurant_name IS NOT NULL
  AND HEX(CONVERT(restaurant_name USING latin1)) NOT LIKE '%3F%'
  AND BINARY restaurant_name <> BINARY CONVERT(CAST(CONVERT(restaurant_name USING latin1) AS BINARY) USING utf8mb4);

-- 餐厅位置。
UPDATE restaurants
SET location = CONVERT(CAST(CONVERT(location USING latin1) AS BINARY) USING utf8mb4)
WHERE location IS NOT NULL
  AND HEX(CONVERT(location USING latin1)) NOT LIKE '%3F%'
  AND BINARY location <> BINARY CONVERT(CAST(CONVERT(location USING latin1) AS BINARY) USING utf8mb4);
