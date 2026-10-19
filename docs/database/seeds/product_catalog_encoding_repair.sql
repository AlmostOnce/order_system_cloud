-- 仅修复旧版演示数据的分类名称；根据稳定商品编码定位，不依赖本机自增 ID。
-- 请先备份，再与 product_catalog_dev.sql 在同一事务中执行。
SET NAMES utf8mb4;
USE product_db;

-- 保留分类 ID，避免重导时因乱码名称与中文名称不同而产生重复分类。
UPDATE product_categories c
JOIN products p ON p.category_id = c.category_id AND p.window_id = c.window_id
JOIN dining_windows w ON w.window_id = p.window_id
SET c.category_name = CASE p.product_code
    WHEN 'DEMO-RICE-01' THEN '主食'
    WHEN 'DEMO-DRINK-01' THEN '饮品'
    WHEN 'DEMO-HIDDEN-01' THEN '停用分类示例'
END
WHERE w.window_code = 'W-DEMO-01'
  AND p.product_code IN ('DEMO-RICE-01', 'DEMO-DRINK-01', 'DEMO-HIDDEN-01');
