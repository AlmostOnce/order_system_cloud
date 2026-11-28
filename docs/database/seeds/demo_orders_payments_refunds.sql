-- 管理端订单履约演示数据：订单、订单明细、支付单、退款单与审批轨迹。
-- 覆盖待支付/已支付/制作中/待取货/已完成/已取消六种状态，以及待审批/申诉/已退款/已拒绝四类退款。
-- 目的：让取餐核销、退款审批、订单状态推进等管理页面有可操作的真实结构数据。
-- 幂等策略：订单按 order_no 去重，明细按“该订单尚无明细”判断，支付单按 payment_no 去重，
--           退款按 refund_no 去重，审批轨迹按“该退款尚无对应动作”判断；重复执行不重复插入、不重复累计退款数量。
-- 说明：待支付订单与待审批退款的截止时间统一放宽到 2026-12-31，避免演示期间被超时任务自动取消或自动退款。
SET NAMES utf8mb4;

USE order_db;
START TRANSACTION;

-- 1. 订单头定义：固定订单号与状态，日期集中在 2026-11-10 至 2026-11-28。
CREATE
TEMPORARY TABLE demo_order_seed (
    order_no VARCHAR(32) NOT NULL PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    window_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL,
    completed_at DATETIME NULL,
    pickup_code VARCHAR(16) NULL,
    remark VARCHAR(255) NULL
);

INSERT INTO demo_order_seed (order_no, user_id, window_id, status, created_at, completed_at, pickup_code, remark)
VALUES
    ('DM261110001', 1, 1, '已完成', '2026-11-10 11:20:00', '2026-11-10 11:55:00', NULL, '少放辣'),
    ('DM261110002', 2, 3, '已完成', '2026-11-10 12:05:00', '2026-11-10 12:40:00', NULL, NULL),
    ('DM261111003', 3, 4, '已完成', '2026-11-11 11:45:00', '2026-11-11 12:20:00', NULL, '面条硬一点'),
    ('DM261111004', 4, 5, '已完成', '2026-11-11 12:30:00', '2026-11-11 13:05:00', NULL, NULL),
    ('DM261112005', 5, 6, '已完成', '2026-11-12 11:10:00', '2026-11-12 11:45:00', NULL, NULL),
    ('DM261112006', 6, 7, '已完成', '2026-11-12 12:15:00', '2026-11-12 12:50:00', NULL, '不要香菜'),
    ('DM261113007', 1, 8, '已完成', '2026-11-13 11:35:00', '2026-11-13 12:10:00', NULL, NULL),
    ('DM261113008', 2, 9, '已完成', '2026-11-13 12:40:00', '2026-11-13 13:15:00', NULL, NULL),
    ('DM261114009', 3, 10, '已完成', '2026-11-14 11:25:00', '2026-11-14 12:00:00', NULL, NULL),
    ('DM261114010', 4, 1, '已完成', '2026-11-14 12:10:00', '2026-11-14 12:45:00', NULL, NULL),
    ('DM261115011', 5, 3, '已完成', '2026-11-15 11:50:00', '2026-11-15 12:25:00', NULL, NULL),
    ('DM261115012', 6, 4, '已完成', '2026-11-15 12:20:00', '2026-11-15 12:55:00', NULL, NULL),
    ('DM261116013', 1, 5, '已完成', '2026-11-16 11:15:00', '2026-11-16 11:50:00', NULL, NULL),
    ('DM261116014', 2, 6, '已完成', '2026-11-16 12:35:00', '2026-11-16 13:10:00', NULL, '打包'),
    ('DM261117015', 3, 7, '已完成', '2026-11-17 11:40:00', '2026-11-17 12:15:00', NULL, NULL),
    ('DM261117016', 4, 8, '已完成', '2026-11-17 12:25:00', '2026-11-17 13:00:00', NULL, NULL),
    ('DM261118017', 5, 9, '已完成', '2026-11-18 11:30:00', '2026-11-18 12:05:00', NULL, NULL),
    ('DM261118018', 6, 10, '已完成', '2026-11-18 12:45:00', '2026-11-18 13:20:00', NULL, NULL),
    ('DM261119019', 1, 1, '已完成', '2026-11-19 11:05:00', '2026-11-19 11:40:00', NULL, NULL),
    ('DM261119020', 2, 3, '已完成', '2026-11-19 12:15:00', '2026-11-19 12:50:00', NULL, NULL),
    ('DM261120021', 3, 4, '已完成', '2026-11-20 11:55:00', '2026-11-20 12:30:00', NULL, NULL),
    ('DM261120022', 4, 5, '已完成', '2026-11-20 12:30:00', '2026-11-20 13:05:00', NULL, NULL),
    ('DM261127023', 5, 6, '待取货', '2026-11-27 11:20:00', NULL, '102301', NULL),
    ('DM261127024', 6, 7, '待取货', '2026-11-27 12:00:00', NULL, '102302', NULL),
    ('DM261127025', 1, 8, '待取货', '2026-11-27 12:30:00', NULL, '102303', NULL),
    ('DM261127026', 2, 9, '待取货', '2026-11-27 11:45:00', NULL, '102304', '不要醋'),
    ('DM261128027', 3, 10, '制作中', '2026-11-28 09:10:00', NULL, '102305', NULL),
    ('DM261128028', 4, 1, '制作中', '2026-11-28 09:25:00', NULL, '102306', NULL),
    ('DM261128029', 5, 3, '制作中', '2026-11-28 09:40:00', NULL, '102307', NULL),
    ('DM261128030', 6, 4, '已支付', '2026-11-28 09:55:00', NULL, '102308', NULL),
    ('DM261128031', 1, 5, '已支付', '2026-11-28 10:05:00', NULL, '102309', NULL),
    ('DM261128032', 2, 6, '已支付', '2026-11-28 10:20:00', NULL, '102310', NULL),
    ('DM261128033', 3, 7, '已支付', '2026-11-28 10:35:00', NULL, '102311', NULL),
    ('DM261128034', 4, 8, '待支付', '2026-11-28 10:45:00', NULL, NULL, NULL),
    ('DM261128035', 5, 9, '待支付', '2026-11-28 10:50:00', NULL, NULL, NULL),
    ('DM261128036', 6, 10, '待支付', '2026-11-28 10:55:00', NULL, NULL, NULL),
    ('DM261126037', 1, 1, '已取消', '2026-11-26 11:30:00', NULL, NULL, NULL),
    ('DM261126038', 2, 3, '已取消', '2026-11-26 12:20:00', NULL, NULL, NULL),
    ('DM261125039', 3, 4, '已完成', '2026-11-25 11:40:00', '2026-11-25 12:10:00', NULL, NULL),
    ('DM261125040', 4, 5, '已完成', '2026-11-25 12:30:00', '2026-11-25 13:00:00', NULL, NULL);

-- 2. 订单头：已存在同号订单时跳过；金额先置 0，待明细写入后回填。
INSERT INTO orders (order_no, user_id, window_id, total_amount, status, remark, pickup_code, version,
                    created_at, paid_at, updated_at, expires_at, completed_at, discount_amount, payable_amount)
SELECT seed.order_no,
       seed.user_id,
       seed.window_id,
       0.00,
       seed.status,
       seed.remark,
       seed.pickup_code,
       0,
       seed.created_at,
       CASE WHEN seed.status IN ('已支付', '制作中', '待取货', '已完成')
                THEN DATE_ADD(seed.created_at, INTERVAL 1 MINUTE) END,
       COALESCE(seed.completed_at, DATE_ADD(seed.created_at, INTERVAL 1 MINUTE), seed.created_at),
       CASE WHEN seed.status = '待支付' THEN '2026-12-31 23:59:59'
            ELSE DATE_ADD(seed.created_at, INTERVAL 15 MINUTE) END,
       seed.completed_at,
       0.00,
       0.00
FROM demo_order_seed seed
WHERE NOT EXISTS (SELECT 1 FROM orders existing WHERE existing.order_no = seed.order_no);

-- 2.1 归一化演示订单取餐码：核销接口只接受六位数字，重复执行时同步修正历史演示行。
UPDATE orders target
    JOIN demo_order_seed seed ON seed.order_no = target.order_no
SET target.pickup_code = seed.pickup_code
WHERE seed.pickup_code IS NOT NULL
  AND target.pickup_code <> seed.pickup_code;

-- 3. 每个档口选两道在售菜品（优先价格较高、分类启用的菜品），用于生成订单明细。
CREATE
TEMPORARY TABLE demo_product_pick AS
SELECT ranked.window_id, ranked.product_id, ranked.product_code, ranked.product_name, ranked.price
FROM (SELECT p.window_id,
             p.product_id,
             p.product_code,
             p.product_name,
             p.price,
             ROW_NUMBER() OVER (PARTITION BY p.window_id ORDER BY p.price DESC, p.product_id) AS rn
      FROM product_db.products p
      WHERE p.sale_status = 1
        AND (p.category_id IS NULL
          OR p.category_id IN (SELECT category_id FROM product_db.product_categories WHERE status = 1))) ranked
WHERE ranked.rn <= 2;

-- 4. 订单明细：每单两道菜，数量按订单号与菜品编号稳定取值，金额等于单价乘数量。
INSERT INTO order_items (order_id, product_id, product_code_snapshot, product_name_snapshot,
                         unit_price_snapshot, selection_key_snapshot, specification_text_snapshot,
                         specification_selection_snapshot, quantity, line_amount, created_at,
                         line_discount_amount, line_payable_amount)
SELECT o.order_id,
       pick.product_id,
       pick.product_code,
       pick.product_name,
       pick.price,
       'base',
       '',
       JSON_ARRAY(),
       1 + MOD(CRC32(CONCAT(seed.order_no, '-', pick.product_id)), 2),
       ROUND(pick.price * (1 + MOD(CRC32(CONCAT(seed.order_no, '-', pick.product_id)), 2)), 2),
       DATE_ADD(o.created_at, INTERVAL 1 MINUTE),
       0.00,
       ROUND(pick.price * (1 + MOD(CRC32(CONCAT(seed.order_no, '-', pick.product_id)), 2)), 2)
FROM demo_order_seed seed
         JOIN orders o ON o.order_no = seed.order_no
         JOIN demo_product_pick pick ON pick.window_id = seed.window_id
WHERE NOT EXISTS (SELECT 1 FROM order_items item WHERE item.order_id = o.order_id);

-- 5. 回填订单金额；只处理金额仍为 0 的演示订单，避免覆盖真实订单金额。
UPDATE orders o
    JOIN (SELECT order_id, SUM(line_amount) AS amount FROM order_items GROUP BY order_id) totals
    ON totals.order_id = o.order_id
SET o.total_amount   = totals.amount,
    o.payable_amount = totals.amount
WHERE o.total_amount = 0
  AND o.order_no IN (SELECT order_no FROM demo_order_seed);

-- 6. 支付单：已取消订单不生成支付记录；待支付订单生成进行中的 MOCK 支付单，其余为成功支付。
-- 支付库与订单库同实例，直接跨库写入，避免切换当前库后临时表不可见。
INSERT INTO payment_db.payments (payment_no, order_id, order_no, user_id, channel_code, amount, paid_amount,
                      channel_trade_no, status, fail_reason, notify_count, last_notify_at, success_time,
                      created_at, updated_at)
SELECT CONCAT('DM-PAY-', SUBSTRING(o.order_no, 3)),
       o.order_id,
       o.order_no,
       o.user_id,
       'MOCK',
       o.payable_amount,
       CASE WHEN o.status = '待支付' THEN NULL ELSE o.payable_amount END,
       CASE WHEN o.status = '待支付' THEN NULL ELSE CONCAT('MOCK-', o.order_no) END,
       CASE WHEN o.status = '待支付' THEN 'CREATED' ELSE 'SUCCESS' END,
       NULL,
       CASE WHEN o.status = '待支付' THEN 0 ELSE 1 END,
       CASE WHEN o.status = '待支付' THEN NULL ELSE o.paid_at END,
       CASE WHEN o.status = '待支付' THEN NULL ELSE o.paid_at END,
       o.created_at,
       COALESCE(o.paid_at, o.created_at)
FROM order_db.orders o
         JOIN demo_order_seed seed ON seed.order_no = o.order_no
WHERE o.status <> '已取消'
  AND NOT EXISTS (SELECT 1 FROM payment_db.payments existing
                  WHERE existing.payment_no = CONCAT('DM-PAY-', SUBSTRING(o.order_no, 3)));

-- 7. 退款单：待审批 4 条、已申诉 2 条、已退款 1 条、已拒绝 1 条；每条退款取该订单的第一条明细。
CREATE
TEMPORARY TABLE demo_refund_seed (
    refund_no VARCHAR(32) NOT NULL PRIMARY KEY,
    request_key VARCHAR(64) NOT NULL,
    order_no VARCHAR(32) NOT NULL,
    quantity INT NOT NULL,
    status VARCHAR(24) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    decision_reason VARCHAR(500) NULL,
    decided_by BIGINT NULL,
    created_at DATETIME NOT NULL,
    due_at DATETIME NOT NULL
);

INSERT INTO demo_refund_seed (refund_no, request_key, order_no, quantity, status, reason, decision_reason,
                              decided_by, created_at, due_at)
VALUES
    ('DM-RF-001', 'demo-refund-001', 'DM261127023', 1, 'PENDING', '出餐太慢，临时有事不等了', NULL, NULL,
     '2026-11-27 12:10:00', '2026-12-31 23:59:59'),
    ('DM-RF-002', 'demo-refund-002', 'DM261128027', 1, 'PENDING', '口味和商品描述不一致', NULL, NULL,
     '2026-11-28 09:20:00', '2026-12-31 23:59:59'),
    ('DM-RF-003', 'demo-refund-003', 'DM261128030', 1, 'PENDING', '点错菜品，希望退款', NULL, NULL,
     '2026-11-28 10:05:00', '2026-12-31 23:59:59'),
    ('DM-RF-004', 'demo-refund-004', 'DM261128031', 1, 'PENDING', '等待时间超过 30 分钟', NULL, NULL,
     '2026-11-28 10:20:00', '2026-12-31 23:59:59'),
    ('DM-RF-005', 'demo-refund-005', 'DM261127024', 1, 'APPEALED', '菜品中发现异物', '照片不清晰，暂不通过', 9,
     '2026-11-27 12:20:00', '2026-11-30 12:20:00'),
    ('DM-RF-006', 'demo-refund-006', 'DM261128028', 1, 'APPEALED', '份量明显不足', '无法核实份量，暂不通过', 9,
     '2026-11-28 09:40:00', '2026-12-01 09:40:00'),
    ('DM-RF-007', 'demo-refund-007', 'DM261127025', 1, 'SUCCESS', '汤汁洒漏，影响食用', '已同意退款', 9,
     '2026-11-27 12:45:00', '2026-11-30 12:45:00'),
    ('DM-RF-008', 'demo-refund-008', 'DM261127026', 1, 'REJECTED', '临时不想要了', '餐品已制作完成，无法退款', 9,
     '2026-11-27 12:00:00', '2026-11-30 12:00:00');

INSERT INTO order_refunds (refund_no, request_key, order_id, user_id, item_id, product_id, product_name,
                           quantity, amount, reason, status, original_status, restock, decision_reason,
                           decided_by, due_at, next_attempt_at, attempts, created_at, updated_at)
SELECT seed.refund_no,
       seed.request_key,
       o.order_id,
       o.user_id,
       item.item_id,
       item.product_id,
       item.product_name_snapshot,
       seed.quantity,
       ROUND(item.line_payable_amount / item.quantity * seed.quantity, 2),
       seed.reason,
       seed.status,
       o.status,
       0,
       seed.decision_reason,
       seed.decided_by,
       seed.due_at,
       seed.created_at,
       CASE WHEN seed.status = 'SUCCESS' THEN 1 ELSE 0 END,
       seed.created_at,
       seed.created_at
FROM demo_refund_seed seed
         JOIN orders o ON o.order_no = seed.order_no
         JOIN order_items item ON item.item_id = (SELECT MIN(first_item.item_id)
                                                 FROM order_items first_item
                                                 WHERE first_item.order_id = o.order_id)
WHERE NOT EXISTS (SELECT 1 FROM order_refunds existing WHERE existing.refund_no = seed.refund_no);

-- 8. 审批轨迹：申请必然存在；申诉补商家拒绝与学生申诉；已退款补同意与到账；已拒绝补商家拒绝。
INSERT INTO order_refund_actions (refund_id, actor_id, action, detail, created_at)
SELECT r.refund_id, r.user_id, 'APPLY', r.reason, r.created_at
FROM order_refunds r
WHERE r.refund_no LIKE 'DM-RF-%'
  AND NOT EXISTS (SELECT 1 FROM order_refund_actions a WHERE a.refund_id = r.refund_id AND a.action = 'APPLY');

INSERT INTO order_refund_actions (refund_id, actor_id, action, detail, created_at)
SELECT r.refund_id, r.decided_by, 'REJECT', COALESCE(r.decision_reason, '商家拒绝退款'),
       DATE_ADD(r.created_at, INTERVAL 10 MINUTE)
FROM order_refunds r
WHERE r.refund_no LIKE 'DM-RF-%'
  AND r.status IN ('APPEALED', 'REJECTED')
  AND NOT EXISTS (SELECT 1 FROM order_refund_actions a WHERE a.refund_id = r.refund_id AND a.action = 'REJECT');

INSERT INTO order_refund_actions (refund_id, actor_id, action, detail, created_at)
SELECT r.refund_id, r.user_id, 'APPEAL', '请求食堂经理或总经理介入', DATE_ADD(r.created_at, INTERVAL 20 MINUTE)
FROM order_refunds r
WHERE r.refund_no LIKE 'DM-RF-%'
  AND r.status = 'APPEALED'
  AND NOT EXISTS (SELECT 1 FROM order_refund_actions a WHERE a.refund_id = r.refund_id AND a.action = 'APPEAL');

INSERT INTO order_refund_actions (refund_id, actor_id, action, detail, created_at)
SELECT r.refund_id, r.decided_by, 'APPROVE', COALESCE(r.decision_reason, '同意退款'),
       DATE_ADD(r.created_at, INTERVAL 10 MINUTE)
FROM order_refunds r
WHERE r.refund_no LIKE 'DM-RF-%'
  AND r.status = 'SUCCESS'
  AND NOT EXISTS (SELECT 1 FROM order_refund_actions a WHERE a.refund_id = r.refund_id AND a.action = 'APPROVE');

INSERT INTO order_refund_actions (refund_id, actor_id, action, detail, created_at)
SELECT r.refund_id, NULL, 'SUCCESS', '资金退款已完成', DATE_ADD(r.created_at, INTERVAL 20 MINUTE)
FROM order_refunds r
WHERE r.refund_no LIKE 'DM-RF-%'
  AND r.status = 'SUCCESS'
  AND NOT EXISTS (SELECT 1 FROM order_refund_actions a WHERE a.refund_id = r.refund_id AND a.action = 'SUCCESS');

-- 9. 同步明细退款份数：按退款单绝对回填，重复执行不会累加。
UPDATE order_items item
    JOIN (SELECT item_id, MAX(quantity) AS quantity FROM order_refunds
          WHERE refund_no LIKE 'DM-RF-%' GROUP BY item_id) refunded ON refunded.item_id = item.item_id
SET item.refund_reserved_quantity = GREATEST(item.refund_reserved_quantity, refunded.quantity);

UPDATE order_items item
    JOIN (SELECT item_id, MAX(quantity) AS quantity FROM order_refunds
          WHERE refund_no LIKE 'DM-RF-%' AND status IN ('APPROVED', 'PROCESSING', 'SUCCESS')
          GROUP BY item_id) approved ON approved.item_id = item.item_id
SET item.refund_approved_quantity = GREATEST(item.refund_approved_quantity, approved.quantity);

UPDATE order_items item
    JOIN (SELECT item_id, MAX(quantity) AS quantity FROM order_refunds
          WHERE refund_no LIKE 'DM-RF-%' AND status = 'SUCCESS'
          GROUP BY item_id) completed ON completed.item_id = item.item_id
SET item.refunded_quantity = GREATEST(item.refunded_quantity, completed.quantity);

DROP TEMPORARY TABLE demo_refund_seed;
DROP TEMPORARY TABLE demo_product_pick;
DROP TEMPORARY TABLE demo_order_seed;

COMMIT;

-- 执行后核对：订单 40 条、明细 80 条、退款 8 条，状态分布符合演示预期。
SELECT status, COUNT(*) AS order_count FROM orders WHERE order_no LIKE 'DM26%' GROUP BY status ORDER BY status;
SELECT COUNT(*) AS demo_item_count FROM order_items i JOIN orders o ON o.order_id = i.order_id WHERE o.order_no LIKE 'DM26%';
SELECT status, COUNT(*) AS refund_count FROM order_refunds WHERE refund_no LIKE 'DM-RF-%' GROUP BY status ORDER BY status;
SELECT COUNT(*) AS payment_count FROM payment_db.payments WHERE payment_no LIKE 'DM-PAY-%';
