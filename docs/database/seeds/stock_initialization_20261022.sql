-- 2026-10-22 按用户授权随机生成 20-100 份初始库存；本脚本由具备 product_db 只读、stock_db 写权限的账号执行。
-- 本文件为本次初始化实际使用的数量记录；已有库存仍不会被覆盖。
USE stock_db;

DROP TEMPORARY TABLE IF EXISTS stock_seed_input;
CREATE TEMPORARY TABLE stock_seed_input (
    window_id BIGINT UNSIGNED NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL,
    initial_qty INT NOT NULL CHECK (initial_qty >= 0),
    PRIMARY KEY (window_id, product_id)
);

-- 在这里填写并取消注释，三列依次是：窗口 ID、菜品 ID、真实初始库存。
-- 示例仅说明格式，请替换成你的数据：
-- INSERT INTO stock_seed_input (window_id, product_id, initial_qty) VALUES
--     (1, 101, 50),
--     (1, 102, 30);

INSERT INTO stock_seed_input (window_id, product_id, initial_qty) VALUES
    (1, 1, 25),
    (1, 2, 66),
    (1, 3, 89),
    (1, 4, 63),
    (1, 5, 45);

-- 先检查输入非空，且每道菜品确实属于指定窗口；任何失败都会阻止后续初始化。
-- 使用 MySQL 8 CHECK 做断言，不创建永久存储过程，不使用 --force 忽略错误。
DROP TEMPORARY TABLE IF EXISTS stock_seed_guard;
CREATE TEMPORARY TABLE stock_seed_guard (valid TINYINT NOT NULL CHECK (valid = 1));
INSERT INTO stock_seed_guard (valid)
SELECT CASE WHEN COUNT(*) > 0 AND COUNT(*) = COUNT(p.product_id) THEN 1 ELSE 0 END
FROM stock_seed_input i
LEFT JOIN product_db.products p ON p.product_id = i.product_id AND p.window_id = i.window_id;

START TRANSACTION;

-- 仅创建缺失记录，不覆盖已售出后的库存，也不恢复逻辑删除的库存。
-- total_qty 是对账的初始基线，available_qty 与它同时初始化；初始值不另记变动流水。
INSERT INTO stock (window_id, product_id, total_qty, available_qty, version, is_deleted)
SELECT i.window_id, i.product_id, i.initial_qty, i.initial_qty, 0, 0
FROM stock_seed_input i
JOIN stock_seed_guard g ON g.valid = 1
LEFT JOIN stock existing ON existing.window_id = i.window_id AND existing.product_id = i.product_id
WHERE existing.stock_id IS NULL
ON DUPLICATE KEY UPDATE stock_id = stock.stock_id;

COMMIT;

-- 核对实际结果；现有库存行不一定等于输入数量，这是防止重复初始化覆盖库存的预期行为。
SELECT s.window_id, s.product_id, p.product_name, s.total_qty, s.available_qty, s.is_deleted
FROM stock s
JOIN stock_seed_input i ON i.window_id = s.window_id AND i.product_id = s.product_id
JOIN product_db.products p ON p.product_id = s.product_id AND p.window_id = s.window_id
ORDER BY s.window_id, s.product_id;

DROP TEMPORARY TABLE stock_seed_guard;
DROP TEMPORARY TABLE stock_seed_input;
