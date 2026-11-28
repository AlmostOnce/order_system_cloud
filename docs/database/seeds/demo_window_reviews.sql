-- 管理端评价管理演示数据：店铺评价与评价讨论。
-- 评价只挂在“已完成”订单上，满足一单一评的业务约束；讨论覆盖商家回复、学生追问和商家二次回复。
-- 幂等策略：评价按 order_id 去重，讨论按 discussion_id 去重；重复执行不重复插入。
-- 说明：本轮不生成图片文件，因此 image_file_keys 统一为空数组，带图筛选暂时没有演示数据。
SET NAMES utf8mb4;
USE comment_db;
START TRANSACTION;

-- 1. 从演示订单中取前 20 条已完成订单作为评价来源，序号用于生成稳定的 UUID。
CREATE
TEMPORARY TABLE demo_review_target AS
SELECT ranked.order_id, ranked.user_id, ranked.window_id, ranked.completed_at, ranked.username, ranked.seq
FROM (SELECT o.order_id,
             o.user_id,
             o.window_id,
             o.completed_at,
             u.username,
             ROW_NUMBER() OVER (ORDER BY o.order_id) AS seq
      FROM order_db.orders o
               JOIN user_db.users u ON u.user_id = o.user_id
      WHERE o.order_no IN ('DM261110001', 'DM261110002', 'DM261111003', 'DM261111004', 'DM261112005',
                           'DM261112006', 'DM261113007', 'DM261113008', 'DM261114009', 'DM261114010',
                           'DM261115011', 'DM261115012', 'DM261116013', 'DM261116014', 'DM261117015',
                           'DM261117016', 'DM261118017', 'DM261118018', 'DM261119019', 'DM261119020')) ranked;

-- 2. 写入店铺评价：评分按订单主键稳定分布在 1-5 分，正文随评分变化。
INSERT INTO window_reviews (review_id, user_id, author_name, order_id, window_id, score, content,
                            image_file_keys, tags, request_key, request_hash, created_at, anonymous, status)
SELECT CONCAT('d0000000-0000-4000-8000-', LPAD(target.seq, 12, '0')),
       target.user_id,
       target.username,
       target.order_id,
       target.window_id,
       1 + MOD(target.order_id, 5),
       CASE 1 + MOD(target.order_id, 5)
           WHEN 5 THEN '味道很好，出餐也快，下次还会再来。'
           WHEN 4 THEN '整体不错，分量足，性价比高。'
           WHEN 3 THEN '中规中矩，和预期差不多。'
           WHEN 2 THEN '口味偏咸，等待时间有点长。'
           ELSE '菜品送到时已经偏凉，体验一般。'
           END,
       JSON_ARRAY(),
       JSON_ARRAY('演示评价', '校园食堂'),
       CONCAT('demo-window-review-', target.order_id),
       SHA2(CONCAT('demo-window-review-', target.order_id), 256),
       DATE_ADD(target.completed_at, INTERVAL 30 MINUTE),
       CASE WHEN MOD(target.seq, 7) = 0 THEN 1 ELSE 0 END,
       1
FROM demo_review_target target
WHERE NOT EXISTS (SELECT 1 FROM window_reviews existing WHERE existing.order_id = target.order_id);

-- 3. 讨论定义：前 6 条为商家直接回复，后 4 组为学生追问 + 商家回复（depth=2）。
CREATE
TEMPORARY TABLE demo_discussion_seed (
    seq INT NOT NULL PRIMARY KEY,
    root_seq INT NULL,
    order_no VARCHAR(32) NOT NULL,
    author_id BIGINT NOT NULL,
    author_role VARCHAR(24) NOT NULL,
    author_name VARCHAR(64) NOT NULL,
    content VARCHAR(500) NOT NULL,
    offset_min INT NOT NULL
);

INSERT INTO demo_discussion_seed (seq, root_seq, order_no, author_id, author_role, author_name, content, offset_min)
VALUES (1, NULL, 'DM261110001', 10, 'STALL_MANAGER', '家常小炒负责人', '谢谢支持，欢迎常来！', 120),
       (2, NULL, 'DM261110002', 9, 'RESTAURANT_MANAGER', '桂园餐厅经理', '感谢反馈，我们会继续保持。', 120),
       (3, NULL, 'DM261111003', 10, 'STALL_MANAGER', '手工面馆负责人', '面条软硬可以提前备注，下次为您调整。', 120),
       (4, NULL, 'DM261111004', 9, 'RESTAURANT_MANAGER', '桃园餐厅经理', '感谢认可，蒸菜每天现做。', 120),
       (5, NULL, 'DM261112005', 11, 'STALL_MANAGER', '铁板烧饭负责人', '出餐速度我们会继续优化。', 120),
       (6, NULL, 'DM261112006', 12, 'STALL_MANAGER', '特色米粉档口', '已记录您的建议，欢迎再次光临。', 120),
       (7, NULL, 'DM261113007', 1, 'CUSTOMER', '小明', '请问这家档口周末也营业吗？', 120),
       (8, 7, 'DM261113007', 10, 'STALL_MANAGER', '招牌盖饭负责人', '周末正常营业，10:30 开始供餐。', 180),
       (9, NULL, 'DM261113008', 2, 'CUSTOMER', '小周', '水饺可以少放辣吗？', 120),
       (10, 9, 'DM261113008', 13, 'STALL_MANAGER', '饺子馄饨档口', '可以，下单时备注少辣即可。', 180),
       (11, NULL, 'DM261114009', 3, 'CUSTOMER', '小林', '轻食档口有素食选项吗？', 120),
       (12, 11, 'DM261114009', 14, 'STALL_MANAGER', '轻食简餐负责人', '有香煎豆腐藜麦饭等素食可选。', 180);

-- 4. 写入讨论；根讨论 root_id 指向自身，二级回复的 root_id 与 parent_id 均指向根讨论。
INSERT INTO review_discussion (discussion_id, review_type, review_id, window_id, root_id, parent_id, depth,
                               author_id, author_role, author_name, content, status, version, request_key,
                               created_at)
SELECT CONCAT('d1000000-0000-4000-8000-', LPAD(seed.seq, 12, '0')),
       'WINDOW_REVIEW',
       review.review_id,
       review.window_id,
       CONCAT('d1000000-0000-4000-8000-', LPAD(COALESCE(seed.root_seq, seed.seq), 12, '0')),
       CASE WHEN seed.root_seq IS NULL THEN NULL
            ELSE CONCAT('d1000000-0000-4000-8000-', LPAD(seed.root_seq, 12, '0')) END,
       CASE WHEN seed.root_seq IS NULL THEN 1 ELSE 2 END,
       seed.author_id,
       seed.author_role,
       seed.author_name,
       seed.content,
       1,
       1,
       CONCAT('demo-discussion-', seed.seq),
       DATE_ADD(review.created_at, INTERVAL seed.offset_min MINUTE)
FROM demo_discussion_seed seed
         JOIN order_db.orders o ON o.order_no = seed.order_no
         JOIN window_reviews review ON review.order_id = o.order_id
WHERE NOT EXISTS (SELECT 1 FROM review_discussion existing
                  WHERE existing.discussion_id = CONCAT('d1000000-0000-4000-8000-', LPAD(seed.seq, 12, '0')));

DROP TEMPORARY TABLE demo_discussion_seed;
DROP TEMPORARY TABLE demo_review_target;

COMMIT;

-- 执行后核对：评价 20 条、讨论 12 条，评分分布覆盖 1-5 分。
SELECT score, COUNT(*) AS review_count FROM window_reviews WHERE review_id LIKE 'd0000000-0000-4000-8000-%' GROUP BY score ORDER BY score;
SELECT COUNT(*) AS discussion_count FROM review_discussion WHERE discussion_id LIKE 'd1000000-0000-4000-8000-%';
