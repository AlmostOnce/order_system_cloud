-- 管理端售后工单演示数据：用餐问题与软件问题，覆盖待受理、处理中、已关闭三种状态。
-- 时间线包含学生补充、工作人员公开回复、内部备注与系统状态消息，便于验证内部备注只在管理端展示。
-- 幂等策略：工单按 ticket_id 去重，消息按 message_id 去重；重复执行不重复插入。
SET NAMES utf8mb4;
USE support_db;
START TRANSACTION;

-- 1. 工单定义；用餐问题通过订单号关联订单并反查窗口与餐厅，软件问题仅记录提交学生。
CREATE
TEMPORARY TABLE demo_ticket_seed (
    seq INT NOT NULL PRIMARY KEY,
    order_no VARCHAR(32) NULL,
    customer_id BIGINT NULL,
    type VARCHAR(16) NOT NULL,
    category VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    last_reply_at DATETIME(3) NULL,
    closed_at DATETIME(3) NULL,
    customer_unread TINYINT NOT NULL
);

INSERT INTO demo_ticket_seed (seq, order_no, customer_id, type, category, status, description,
                              created_at, last_reply_at, closed_at, customer_unread)
VALUES (1, 'DM261114010', NULL, 'DINING', 'HYGIENE', 'PENDING',
        '汤里发现一根头发，希望食堂核实并处理。', '2026-11-14 12:50:00', NULL, NULL, 0),
       (2, 'DM261117016', NULL, 'DINING', 'WRONG_ITEM', 'PENDING',
        '点的是黄焖鸡米饭，送来的却是红烧肉盖饭，希望补一份正确的菜品。', '2026-11-17 13:05:00', NULL, NULL, 0),
       (3, 'DM261118017', NULL, 'DINING', 'WAIT_TIME', 'PROCESSING',
        '取餐等了将近 40 分钟，午休时间比较紧张，希望高峰时段能加快出餐。', '2026-11-18 12:10:00',
        '2026-11-18 12:40:00', NULL, 1),
       (4, 'DM261119020', NULL, 'DINING', 'PORTION_PRICE', 'PROCESSING',
        '牛肉香锅里的牛肉份量比以前少了很多，价格却没变，希望核实标准份量。', '2026-11-19 12:55:00',
        '2026-11-19 13:20:00', NULL, 1),
       (5, 'DM261111004', NULL, 'DINING', 'ATTITUDE', 'CLOSED',
        '档口工作人员回复问题时态度比较冷淡，希望加强服务培训。', '2026-11-11 13:10:00',
        '2026-11-11 15:00:00', '2026-11-12 10:00:00', 0),
       (6, NULL, 1, 'SOFTWARE', 'PAYMENT', 'PENDING',
        '余额支付已经扣款成功，但订单一直显示待支付，请尽快核实。', '2026-11-20 11:20:00', NULL, NULL, 0),
       (7, NULL, 4, 'SOFTWARE', 'ORDER', 'PROCESSING',
        '下单成功后订单列表没有显示新订单，重新进入仍然看不到。', '2026-11-21 18:30:00',
        '2026-11-21 19:00:00', NULL, 1),
       (8, NULL, 6, 'SOFTWARE', 'ACCOUNT', 'CLOSED',
        '无法使用邮箱登录，提示账号不存在，但之前一直可以正常登录。', '2026-11-22 09:10:00',
        '2026-11-22 10:00:00', '2026-11-23 11:00:00', 0);

INSERT INTO support_ticket (ticket_id, customer_id, type, category, status, order_id, order_no, window_id,
                            window_name, restaurant_id, description, image_file_keys, device_info,
                            customer_unread, request_key, request_hash, created_at, updated_at, last_reply_at,
                            closed_at)
SELECT CONCAT('e0000000-0000-4000-8000-', LPAD(seed.seq, 12, '0')),
       COALESCE(o.user_id, seed.customer_id),
       seed.type,
       seed.category,
       seed.status,
       o.order_id,
       o.order_no,
       win.window_id,
       win.window_name,
       win.restaurant_id,
       seed.description,
       JSON_ARRAY(),
       CASE WHEN seed.type = 'SOFTWARE' THEN 'iPhone 15 / iOS 19 / uni-app 3.0' ELSE NULL END,
       seed.customer_unread,
       CONCAT('demo-ticket-', LPAD(seed.seq, 3, '0')),
       SHA2(CONCAT('demo-ticket-', LPAD(seed.seq, 3, '0')), 256),
       seed.created_at,
       COALESCE(seed.closed_at, seed.last_reply_at, seed.created_at),
       seed.last_reply_at,
       seed.closed_at
FROM demo_ticket_seed seed
         LEFT JOIN order_db.orders o ON o.order_no = seed.order_no
         LEFT JOIN product_db.dining_windows win ON win.window_id = o.window_id
WHERE NOT EXISTS (SELECT 1 FROM support_ticket existing
                  WHERE existing.ticket_id = CONCAT('e0000000-0000-4000-8000-', LPAD(seed.seq, 12, '0')));

-- 2. 时间线消息；偏移分钟相对工单创建时间，保证顺序稳定。
CREATE
TEMPORARY TABLE demo_ticket_message_seed (
    seq INT NOT NULL PRIMARY KEY,
    ticket_seq INT NOT NULL,
    sender_type VARCHAR(16) NOT NULL,
    sender_id BIGINT NULL,
    content VARCHAR(1000) NOT NULL,
    internal_note TINYINT NOT NULL,
    offset_min INT NOT NULL
);

INSERT INTO demo_ticket_message_seed (seq, ticket_seq, sender_type, sender_id, content, internal_note, offset_min)
VALUES (1, 1, 'CUSTOMER', NULL, '可以尽快处理吗？菜品已经倒掉了。', 0, 10),
       (2, 2, 'CUSTOMER', NULL, '希望补一份正确的菜品，谢谢。', 0, 15),
       (3, 3, 'SYSTEM', NULL, '工单已受理，工作人员会尽快处理。', 0, 5),
       (4, 3, 'STAFF', 10, '已反馈给档口，今天高峰期确实排队较长，我们会增加出餐人手。', 0, 30),
       (5, 3, 'STAFF', 10, '已与档口负责人确认，周五起高峰时段增加一名出餐员。', 1, 35),
       (6, 4, 'SYSTEM', NULL, '工单已受理，工作人员会尽快处理。', 0, 5),
       (7, 4, 'STAFF', 9, '已核对近期出餐记录，会要求档口按标准份量执行。', 0, 25),
       (8, 4, 'STAFF', 9, '抽查未发现明显缺斤少两，继续观察一周。', 1, 30),
       (9, 5, 'SYSTEM', NULL, '工单已受理，工作人员会尽快处理。', 0, 5),
       (10, 5, 'STAFF', 9, '已对当事员工进行提醒，感谢反馈。', 0, 110),
       (11, 5, 'SYSTEM', NULL, '工单已关闭。', 0, 1300),
       (12, 7, 'SYSTEM', NULL, '工单已受理，正在排查订单数据。', 0, 5),
       (13, 8, 'SYSTEM', NULL, '工单已受理，工作人员会尽快处理。', 0, 5),
       (14, 8, 'STAFF', 7, '已协助重置登录方式，请使用用户号登录后重新绑定邮箱。', 0, 50),
       (15, 8, 'SYSTEM', NULL, '工单已关闭。', 0, 1500);

INSERT INTO support_ticket_message (message_id, ticket_id, sender_type, sender_id, content, image_file_keys,
                                    internal_note, request_key, created_at)
SELECT CONCAT('e1000000-0000-4000-8000-', LPAD(seed.seq, 12, '0')),
       ticket.id,
       seed.sender_type,
       seed.sender_id,
       seed.content,
       JSON_ARRAY(),
       seed.internal_note,
       CASE WHEN seed.sender_type = 'CUSTOMER' THEN CONCAT('demo-ticket-message-', seed.seq) ELSE NULL END,
       DATE_ADD(ticket.created_at, INTERVAL seed.offset_min MINUTE)
FROM demo_ticket_message_seed seed
         JOIN support_ticket ticket
              ON ticket.ticket_id = CONCAT('e0000000-0000-4000-8000-', LPAD(seed.ticket_seq, 12, '0'))
WHERE NOT EXISTS (SELECT 1 FROM support_ticket_message existing
                  WHERE existing.message_id = CONCAT('e1000000-0000-4000-8000-', LPAD(seed.seq, 12, '0')));

DROP TEMPORARY TABLE demo_ticket_message_seed;
DROP TEMPORARY TABLE demo_ticket_seed;

COMMIT;

-- 执行后核对：工单 8 条、消息 15 条，餐厅与窗口快照已回填。
SELECT status, type, COUNT(*) AS ticket_count FROM support_ticket
WHERE ticket_id LIKE 'e0000000-0000-4000-8000-%' GROUP BY status, type ORDER BY status, type;
SELECT COUNT(*) AS message_count FROM support_ticket_message WHERE message_id LIKE 'e1000000-0000-4000-8000-%';
