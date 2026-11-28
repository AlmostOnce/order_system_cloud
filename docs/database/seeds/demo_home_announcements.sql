-- 管理端公告页演示数据。
-- 覆盖草稿、已发布生效、已下线、已发布但尚未生效四种状态，便于验证首页与后台的生效过滤。
-- 幂等策略：以标题作为业务唯一判断，重复执行不会重复插入；不修改已有公告。
SET NAMES utf8mb4;
USE product_db;

CREATE
TEMPORARY TABLE demo_announcement_seed (
    title VARCHAR(60) NOT NULL PRIMARY KEY,
    content VARCHAR(500) NOT NULL,
    status TINYINT NOT NULL,
    start_at DATETIME NOT NULL,
    end_at DATETIME NULL,
    published_at DATETIME NULL,
    created_by BIGINT NOT NULL
);

INSERT INTO demo_announcement_seed (title, content, status, start_at, end_at, published_at, created_by)
VALUES
    ('桂园食堂周末营业时间调整',
     '本周六、周日桂园食堂一楼档口营业时间调整为 10:30-13:30，二楼档口照常营业。请同学们合理安排用餐时间。',
     1, '2026-11-20 00:00:00', NULL, '2026-11-19 16:00:00', 8),
    ('冬季新品上市：麻辣香锅系列',
     '桂园食堂麻辣香锅档口新增肥牛香锅、酱香菌菇香锅两款冬季新品，11月28日起供应，欢迎同学品尝。',
     1, '2026-11-28 00:00:00', '2026-12-15 23:59:59', '2026-11-27 18:00:00', 8),
    ('十二月食堂满意度调查（草稿）',
     '十二月食堂满意度调查问卷正在筹备，计划覆盖三个食堂的全部营业档口，发布后请同学们积极参与。',
     0, '2026-12-01 00:00:00', '2026-12-31 23:59:59', NULL, 7),
    ('国庆假期营业安排',
     '国庆假期期间三个食堂轮流值班，具体营业档口以当日现场公告为准。该公告已下线，仅保留历史记录。',
     2, '2026-09-28 00:00:00', '2026-10-08 23:59:59', '2026-09-27 10:00:00', 7),
    ('校园一卡通系统维护通知',
     '校园一卡通系统将于 12 月 5 日 22:00 至 12 月 6 日 06:00 进行维护，期间无法使用一卡通支付，请提前充值。',
     1, '2026-12-05 22:00:00', '2026-12-06 06:00:00', '2026-11-28 09:00:00', 7);

INSERT INTO home_announcements (title, content, status, start_at, end_at, published_at, created_by, updated_by)
SELECT seed.title,
       seed.content,
       seed.status,
       seed.start_at,
       seed.end_at,
       seed.published_at,
       seed.created_by,
       seed.created_by
FROM demo_announcement_seed seed
WHERE NOT EXISTS (SELECT 1 FROM home_announcements existing WHERE existing.title = seed.title);

DROP TEMPORARY TABLE demo_announcement_seed;

-- 执行后核对：应看到 5 条公告，状态分布 0/1/2 均存在。
SELECT announcement_id, title, status, start_at, end_at, published_at
FROM home_announcements
WHERE title IN ('桂园食堂周末营业时间调整', '冬季新品上市：麻辣香锅系列', '十二月食堂满意度调查（草稿）',
                '国庆假期营业安排', '校园一卡通系统维护通知')
ORDER BY announcement_id;
