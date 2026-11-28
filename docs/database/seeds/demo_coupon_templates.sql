-- 管理端优惠券模板演示数据：覆盖草稿、已发布、停止领取、停止使用四种状态。
-- 其中一条为限档口券，写入 coupon_template_window 关联桂园食堂的三个档口。
-- 幂等策略：以标题作为业务唯一判断，重复执行不重复创建模板；档口关联使用 INSERT IGNORE。
SET NAMES utf8mb4;
USE coupon_db;
START TRANSACTION;

CREATE
TEMPORARY TABLE demo_coupon_seed (
    title VARCHAR(120) NOT NULL PRIMARY KEY,
    min_spend DECIMAL(12, 2) NOT NULL,
    discount_amount DECIMAL(12, 2) NOT NULL,
    total_quantity BIGINT UNSIGNED NOT NULL,
    claimed_quantity BIGINT UNSIGNED NOT NULL,
    claim_start_at DATETIME NOT NULL,
    claim_end_at DATETIME NOT NULL,
    valid_from DATETIME NOT NULL,
    valid_until DATETIME NOT NULL,
    scope_type VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL
);

INSERT INTO demo_coupon_seed (title, min_spend, discount_amount, total_quantity, claimed_quantity,
                              claim_start_at, claim_end_at, valid_from, valid_until, scope_type, status)
VALUES ('新用户立减券', 30.00, 5.00, 500, 0,
        '2026-12-01 00:00:00', '2026-12-31 23:59:59', '2026-12-01 00:00:00', '2027-01-15 23:59:59',
        'CAMPUS', 'DRAFT'),
       ('冬日暖胃券', 20.00, 3.00, 300, 86,
        '2026-11-20 00:00:00', '2026-12-10 23:59:59', '2026-11-20 00:00:00', '2026-12-20 23:59:59',
        'CAMPUS', 'PUBLISHED'),
       ('桂园食堂专享立减', 25.00, 4.00, 150, 40,
        '2026-11-15 00:00:00', '2026-12-05 23:59:59', '2026-11-15 00:00:00', '2026-12-15 23:59:59',
        'WINDOWS', 'PUBLISHED'),
       ('周末加餐券', 40.00, 8.00, 200, 200,
        '2026-11-01 00:00:00', '2026-11-20 23:59:59', '2026-11-01 00:00:00', '2026-11-30 23:59:59',
        'CAMPUS', 'CLAIM_STOPPED'),
       ('冬季特惠券', 50.00, 10.00, 100, 63,
        '2026-11-10 00:00:00', '2026-11-25 23:59:59', '2026-11-10 00:00:00', '2026-12-10 23:59:59',
        'CAMPUS', 'STOPPED');

INSERT INTO coupon_template (title, min_spend, discount_amount, total_quantity, claimed_quantity,
                             claim_start_at, claim_end_at, valid_from, valid_until, scope_type,
                             issuer_type, funding_party, status, created_by)
SELECT seed.title, seed.min_spend, seed.discount_amount, seed.total_quantity, seed.claimed_quantity,
       seed.claim_start_at, seed.claim_end_at, seed.valid_from, seed.valid_until, seed.scope_type,
       'PLATFORM', 'PLATFORM', seed.status, 8
FROM demo_coupon_seed seed
WHERE NOT EXISTS (SELECT 1 FROM coupon_template existing WHERE existing.title = seed.title);

-- 限档口券关联桂园食堂的三个档口（1、3、4）；已存在则跳过。
INSERT IGNORE INTO coupon_template_window (coupon_template_id, window_id)
SELECT template.coupon_template_id, window_ids.window_id
FROM coupon_template template
         JOIN (SELECT 1 AS window_id UNION ALL SELECT 3 UNION ALL SELECT 4) window_ids
WHERE template.title = '桂园食堂专享立减';

DROP TEMPORARY TABLE demo_coupon_seed;

COMMIT;

-- 执行后核对：模板 5 条、档口关联 3 条。
SELECT coupon_template_id, title, status, scope_type, total_quantity, claimed_quantity
FROM coupon_template
WHERE title IN ('新用户立减券', '冬日暖胃券', '桂园食堂专享立减', '周末加餐券', '冬季特惠券')
ORDER BY coupon_template_id;
SELECT COUNT(*) AS window_link_count
FROM coupon_template_window link
         JOIN coupon_template template ON template.coupon_template_id = link.coupon_template_id
WHERE template.title = '桂园食堂专享立减';
